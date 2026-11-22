#pragma once

#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace phoenix { class LibretroCore; }

/** Chamado pela thread do emulador depois de cada retro_run. No-op se o RA nao foi iniciado. */
void phoenix_ra_depois_do_quadro();
/** Chamado enquanto o jogo esta pausado. */
void phoenix_ra_idle();
/** Chamado quando o jogo e reiniciado. */
void phoenix_ra_resetou();
/** Serializa o estado interno do rcheevos (para rewind / save state). */
bool phoenix_ra_serializar_estado(std::vector<uint8_t> &saida);
/** Restaura o estado interno do rcheevos (ou reseta se buffer vazio/invalido). */
void phoenix_ra_desserializar_estado(const uint8_t *dados, size_t tamanho);
/** Salva/carrega o progresso do rcheevos em arquivo sidecar (.ra). */
void phoenix_ra_salvar_arquivo(const std::string &caminho);
void phoenix_ra_carregar_arquivo(const std::string &caminho);

bool phoenix_ra_iniciar(JNIEnv *env, phoenix::LibretroCore *nucleo, const std::string &usuario,
                        const std::string &token, const std::string &hash, int consoleId, bool hardcore);
