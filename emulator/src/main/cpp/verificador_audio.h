/*
 * ============================================================================
 * VERIFICADOR 2 -- saude do buffer de audio
 * ============================================================================
 *
 * Por que existe: "o som ta estalando" nao e diagnostico. Com este contador o
 * relato vira "14 underruns em 60 s, ocupacao media 38%", e ai da para saber
 * se o buffer e pequeno, se o nucleo esta lento ou se o pacing esta errado --
 * tres causas diferentes com o MESMO sintoma no ouvido.
 *
 * Underrun  = o Oboe pediu amostras e o buffer nao tinha. Vira estalo.
 * Overrun   = o nucleo produziu mais rapido que o consumo e o buffer encheu.
 *             Vira atraso crescente entre imagem e som, e depois descarte.
 *
 * Um app saudavel, rodando o nucleo falso por 60 s, tem de marcar ZERO dos
 * dois. Qualquer numero diferente de zero e bug, nao "e assim mesmo".
 *
 * Uso:
 *   audio_saude_iniciar();
 *   ... no produtor:  audio_saude_produziu(n_quadros, ocupacao, capacidade);
 *   ... no consumidor: audio_saude_consumiu(pedidos, entregues);
 *   ... uma vez por segundo: audio_saude_relatar();
 */
#ifndef VERIFICADOR_AUDIO_H
#define VERIFICADOR_AUDIO_H

#include <android/log.h>
#include <stdint.h>
#include <time.h>

#ifdef __cplusplus
#include <atomic>
#define AUDIO_ATOMIC(T) std::atomic<T>
#define AUDIO_LOAD(v) ((v).load(std::memory_order_relaxed))
#define AUDIO_STORE(v, x) ((v).store((x), std::memory_order_relaxed))
#define AUDIO_ADD(v, x) ((v).fetch_add((x), std::memory_order_relaxed))
#else
#include <stdatomic.h>
#define AUDIO_ATOMIC(T) _Atomic T
#define AUDIO_LOAD(v) atomic_load_explicit(&(v), memory_order_relaxed)
#define AUDIO_STORE(v, x) atomic_store_explicit(&(v), (x), memory_order_relaxed)
#define AUDIO_ADD(v, x) atomic_fetch_add_explicit(&(v), (x), memory_order_relaxed)
#endif

#ifndef AUDIO_TAG
#define AUDIO_TAG "PhoenixAudio"
#endif

typedef struct {
   AUDIO_ATOMIC(uint64_t) underruns;          /* pediu e nao tinha                        */
   AUDIO_ATOMIC(uint64_t) overruns;           /* produziu e nao coube                     */
   AUDIO_ATOMIC(uint64_t) quadros_prod;
   AUDIO_ATOMIC(uint64_t) quadros_cons;
   AUDIO_ATOMIC(uint64_t) amostras_perdidas;
   AUDIO_ATOMIC(uint64_t) soma_ocupacao_bp;   /* ocupacao em basis points (0..10000)      */
   AUDIO_ATOMIC(uint64_t) leituras;
   AUDIO_ATOMIC(int64_t)  t0_ms;
} AudioSaude;

static AudioSaude audio_saude;

static int64_t audio_saude_agora_ms(void) {
   struct timespec ts;
   clock_gettime(CLOCK_MONOTONIC, &ts);
   return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void audio_saude_iniciar(void) {
   AUDIO_STORE(audio_saude.underruns, 0);
   AUDIO_STORE(audio_saude.overruns, 0);
   AUDIO_STORE(audio_saude.quadros_prod, 0);
   AUDIO_STORE(audio_saude.quadros_cons, 0);
   AUDIO_STORE(audio_saude.amostras_perdidas, 0);
   AUDIO_STORE(audio_saude.soma_ocupacao_bp, 0);
   AUDIO_STORE(audio_saude.leituras, 0);
   AUDIO_STORE(audio_saude.t0_ms, audio_saude_agora_ms());
}

/* Chame depois de escrever no ring buffer. `coube` e quantos quadros
 * realmente entraram -- se for menor que `quadros`, houve overrun. */
static void audio_saude_produziu(uint32_t quadros, uint32_t coube,
                                 uint32_t ocupacao, uint32_t capacidade) {
   AUDIO_ADD(audio_saude.quadros_prod, quadros);
   if (coube < quadros) {
      AUDIO_ADD(audio_saude.overruns, 1);
      AUDIO_ADD(audio_saude.amostras_perdidas, (uint64_t)(quadros - coube));
   }
   if (capacidade > 0) {
      uint64_t bp = ((uint64_t)ocupacao * 10000ULL) / (uint64_t)capacidade;
      AUDIO_ADD(audio_saude.soma_ocupacao_bp, bp);
      AUDIO_ADD(audio_saude.leituras, 1);
   }
}

/* Chame dentro do callback do Oboe, depois de ler do ring buffer. */
static void audio_saude_consumiu(uint32_t pedidos, uint32_t entregues) {
   AUDIO_ADD(audio_saude.quadros_cons, entregues);
   if (entregues < pedidos) AUDIO_ADD(audio_saude.underruns, 1);
}

static void audio_saude_relatar(void) {
   int64_t  agora      = audio_saude_agora_ms();
   int64_t  t0         = AUDIO_LOAD(audio_saude.t0_ms);
   uint64_t underruns  = AUDIO_LOAD(audio_saude.underruns);
   uint64_t overruns   = AUDIO_LOAD(audio_saude.overruns);
   uint64_t prod       = AUDIO_LOAD(audio_saude.quadros_prod);
   uint64_t cons       = AUDIO_LOAD(audio_saude.quadros_cons);
   uint64_t perdidas   = AUDIO_LOAD(audio_saude.amostras_perdidas);
   uint64_t soma_bp    = AUDIO_LOAD(audio_saude.soma_ocupacao_bp);
   uint64_t leituras   = AUDIO_LOAD(audio_saude.leituras);

   double  seg   = (double)(agora - t0) / 1000.0;
   double  ocup  = leituras
                 ? ((double)soma_bp / (double)leituras) / 100.0
                 : 0.0;
   /* Producao e consumo tem de andar juntos. Uma deriva que CRESCE com o
    * tempo e o sinal de que a taxa do nucleo e a do dispositivo nao batem --
    * o bug mais chato desta fase, porque soa bem nos primeiros segundos. */
   int64_t deriva = (int64_t)prod - (int64_t)cons;

   __android_log_print(
      underruns || overruns ? ANDROID_LOG_WARN : ANDROID_LOG_INFO,
      AUDIO_TAG,
      "%.0fs | underruns=%llu overruns=%llu | ocupacao media %.0f%% | "
      "deriva %lld quadros (%.1f ms) | perdidas=%llu",
      seg,
      (unsigned long long)underruns,
      (unsigned long long)overruns,
      ocup,
      (long long)deriva,
      (double)deriva / 48.0,
      (unsigned long long)perdidas);
}

#endif /* VERIFICADOR_AUDIO_H */
