package com.dfdx047.phoenixemu.emulacao

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.dfdx047.phoenixemu.Jogo
import com.dfdx047.phoenixemu.Sistema
import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.BotaoVirtual
import com.dfdx047.phoenixemu.data.Preferencias
import com.dfdx047.phoenixemu.data.Idioma
import com.dfdx047.phoenixemu.data.combo
import com.dfdx047.phoenixemu.emulator.EmulatorActivity
import com.dfdx047.phoenixemu.emulator.AjustesDeJogo
import com.dfdx047.phoenixemu.emulator.EscalaImagem
import com.dfdx047.phoenixemu.emulator.FiltroImagem
import com.dfdx047.phoenixemu.emulator.ProporcaoImagem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * O unico ponto do app que abre o emulador.
 *
 * O modulo `:emulator` roda em outro processo e nao enxerga nada do app --
 * nem as preferencias, nem o `BotaoVirtual`. Tudo o que ele precisa vai na
 * Intent, montado aqui. Se um dia faltar alguma informacao la dentro, e aqui
 * que ela entra.
 */
object Emulador {

    /**
     * Nomes dos nucleos dentro do APK.
     *
     * Precisam comecar com "lib" e terminar em ".so": o Android so extrai da
     * pasta lib/ do APK os arquivos com esse formato. Um nucleo baixado como
     * `mesen_libretro_android.so` tem de ser RENOMEADO antes de ir para
     * `emulator/src/main/jniLibs/arm64-v8a/`, senao ele e ignorado sem aviso.
     */
    const val NUCLEO_FALSO = "libnucleo_falso.so"
    const val NUCLEO_NES = "libmesen.so"
    const val NUCLEO_SNES = "libsnes9x.so"

    fun nucleoPara(sistema: Sistema): String = when (sistema) {
        Sistema.NES -> NUCLEO_NES
        Sistema.SNES -> NUCLEO_SNES
    }

    /** Ferramenta da Fase 4: roda o nucleo falso, sem ROM. */
    suspend fun abrirNucleoFalso(context: Context, prefs: Preferencias) =
        abrir(context, prefs, NUCLEO_FALSO, rom = null, nomeSave = "nucleo_falso")

    suspend fun abrirJogo(context: Context, prefs: Preferencias, jogo: Jogo) {
        val hashRa = jogo.hashRa?.takeIf { it.isNotBlank() }
            ?: withContext(Dispatchers.IO) {
                runCatching {
                    com.dfdx047.phoenixemu.data.RomHasher.calcular(context, jogo.uri, jogo.extensao, jogo.sistema)?.hashRa
                }.getOrNull()
            }
            ?: ""
        abrir(
            context, prefs,
            nucleo = nucleoPara(jogo.sistema),
            rom = jogo.uriString,
            nomeSave = nomeDeSave(jogo),
            titulo = jogo.nome,
            capa = jogo.capaLocal,
            tempoJogadoMs = jogo.tempoJogadoMinutos.toLong() * 60 * 1000,
            plataforma = when (jogo.sistema) {
                Sistema.NES -> "NES"
                Sistema.SNES -> "SNES"
            },
            raHash = hashRa,
            raConsole = when (jogo.sistema) {
                Sistema.NES -> 7
                Sistema.SNES -> 3
            }
        )
    }

    private suspend fun abrir(
        context: Context,
        prefs: Preferencias,
        nucleo: String,
        rom: String?,
        nomeSave: String,
        titulo: String = "",
        capa: String? = null,
        tempoJogadoMs: Long = 0,
        plataforma: String = "",
        raHash: String = "",
        raConsole: Int = 0,
    ) {
        val intent = withContext(Dispatchers.IO) {
            val idDoJogo = rom?.let { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) } ?: ""

            val (escalaStr, eO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_ESCALA}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_ESCALA}" }, EscalaImagem.AJUSTAR.name)
            val (proporcaoStr, pO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_PROPORCAO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_PROPORCAO}" }, ProporcaoImagem.AUTOMATICA.name)
            val (filtroStr, filO) = prefs.getAjusteString("aj_global_${AjustesDeJogo.CHAVE_FILTRO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_FILTRO}" }, FiltroImagem.NITIDO.name)
            val (mostrarFps, fO) = prefs.getAjusteBoolean("aj_global_${AjustesDeJogo.CHAVE_MOSTRAR_FPS}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_MOSTRAR_FPS}" }, false)
            val (volume, vO) = prefs.getAjusteFloat("aj_global_${AjustesDeJogo.CHAVE_VOLUME}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_VOLUME}" }, 1.0f)
            val (mudo, mO) = prefs.getAjusteBoolean("aj_global_${AjustesDeJogo.CHAVE_MUDO}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_MUDO}" }, false)
            val (velocidadeFF, ffO) = prefs.getAjusteInt("aj_global_${AjustesDeJogo.CHAVE_VELOCIDADE_FF}", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_${AjustesDeJogo.CHAVE_VELOCIDADE_FF}" }, 2)

            val overrides = mutableSetOf<String>()
            if (eO) overrides.add(AjustesDeJogo.CHAVE_ESCALA)
            if (pO) overrides.add(AjustesDeJogo.CHAVE_PROPORCAO)
            if (filO) overrides.add(AjustesDeJogo.CHAVE_FILTRO)
            if (fO) overrides.add(AjustesDeJogo.CHAVE_MOSTRAR_FPS)
            if (vO) overrides.add(AjustesDeJogo.CHAVE_VOLUME)
            if (mO) overrides.add(AjustesDeJogo.CHAVE_MUDO)
            if (ffO) overrides.add(AjustesDeJogo.CHAVE_VELOCIDADE_FF)

            val (overlayJsonRaw, overlayOverride) = prefs.getAjusteString("dummy", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_overlay_controle" }, "")
            if (overlayOverride && overlayJsonRaw.isNotBlank()) overrides.add("overlay_controle")

            val ajustes = AjustesDeJogo(
                escala = runCatching { EscalaImagem.valueOf(escalaStr) }.getOrDefault(EscalaImagem.AJUSTAR),
                proporcao = runCatching { ProporcaoImagem.valueOf(proporcaoStr) }.getOrDefault(ProporcaoImagem.AUTOMATICA),
                filtro = runCatching { FiltroImagem.valueOf(filtroStr) }.getOrDefault(FiltroImagem.NITIDO),
                mostrarFps = mostrarFps,
                volume = volume,
                mudo = mudo,
                velocidadeFF = velocidadeFF,
                overrides = overrides,
                idDoJogo = idDoJogo
            )

            val gson = Gson()
            val overlayGlobal = prefs.overlay.first()
            val overlayConfigEfetivo = if (overlayOverride && overlayJsonRaw.isNotBlank()) {
                gson.fromJson(overlayJsonRaw, com.dfdx047.phoenixemu.data.OverlayConfigNova::class.java)
            } else {
                overlayGlobal
            }
            val overlayFinal = if (overlayConfigEfetivo.controles.isEmpty()) {
                overlayConfigEfetivo.copy(controles = if (plataforma == "SNES") com.dfdx047.phoenixemu.data.OverlayConfigNova.layoutSnes() else com.dfdx047.phoenixemu.data.OverlayConfigNova.layoutNes())
            } else {
                overlayConfigEfetivo
            }
            val overlayJsonStr = gson.toJson(overlayFinal.copy(skinsRapidas = overlayGlobal.skinsRapidas))

            val darkTheme = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val schemeBase = com.dfdx047.phoenixemu.ui.theme.esquemaDeAmostraSemComposable(prefs.tema.value, context, darkTheme)

            val corPrimaria = schemeBase.primary.toArgb()
            val corSuperficie = schemeBase.surface.toArgb()
            val corTexto = schemeBase.onSurface.toArgb()

            val atalhos = prefs.atalhos.value
            val atalhosJson = JSONObject()
            AcaoAtalho.entries.forEach { acao ->
                val combo = atalhos.combo(acao)
                if (combo.isNotEmpty()) {
                    val array = JSONArray()
                    combo.forEach { array.put(it) }
                    atalhosJson.put(acao.name, array)
                } else {
                    atalhosJson.put(acao.name, JSONArray())
                }
            }

            val hcJogo = prefs.getAjusteString("dummy", idDoJogo.takeIf { it.isNotEmpty() }?.let { "aj_jogo_${it}_ra_hardcore" }, "PADRAO").first
            val hcConsole = if (raConsole == 3) prefs.raHardcoreSnes.first() else prefs.raHardcoreNes.first()
            val hcPadrao = prefs.conqHardcore.first()
            val hardcoreFinal = when (if (hcJogo != "PADRAO") hcJogo else hcConsole) {
                "LIGADO" -> true
                "DESLIGADO" -> false
                else -> hcPadrao
            }

            val i = Intent(context, EmulatorActivity::class.java)
                .putExtra(EmulatorActivity.EXTRA_NUCLEO, nucleo)
                .putExtra(EmulatorActivity.EXTRA_NOME_SAVE, nomeSave)
                .putExtra(EmulatorActivity.EXTRA_MAPEAMENTO, mapeamento(prefs))
                .putExtra(EmulatorActivity.EXTRA_OVERLAY, overlayJsonStr)
                .putExtra(EmulatorActivity.EXTRA_ATALHOS, atalhosJson.toString())
                .putExtra(EmulatorActivity.EXTRA_ATALHOS_CFG, gson.toJson(prefs.atalhos.value))
                .putExtra(EmulatorActivity.EXTRA_AUTOSALVAR, prefs.autoSalvar.value)
                .putExtra(EmulatorActivity.EXTRA_AUTOCARREGAR, prefs.autoCarregar.value)
                .putExtra(EmulatorActivity.EXTRA_TEMA, prefs.tema.value.name)
                .putExtra(EmulatorActivity.EXTRA_ACABAMENTO, prefs.acabamento.value.name)
                .putExtra(EmulatorActivity.EXTRA_REDUZIR_EFEITOS, prefs.reduzirEfeitos.value)
                .putExtra(EmulatorActivity.EXTRA_AMOLED, prefs.amoled.value)
                .putExtra(EmulatorActivity.EXTRA_SOMBRAS, prefs.sombras.value)
                .putExtra(EmulatorActivity.EXTRA_IDIOMA, Idioma.atual(context))
                .putExtra(EmulatorActivity.EXTRA_TITULO, titulo)
                .putExtra(EmulatorActivity.EXTRA_PLATAFORMA, plataforma)
                .putExtra("phoenix.ajustes", gson.toJson(ajustes))
                .putExtra(EmulatorActivity.EXTRA_TEMPO_JOGADO_MS, tempoJogadoMs)
                .putExtra("phoenix.cor_primaria", corPrimaria)
                .putExtra("phoenix.cor_superficie", corSuperficie)
                .putExtra("phoenix.cor_texto", corTexto)
                .putExtra("phoenix.menu_estilo", prefs.globalMenuEstilo.first())
                .putExtra("phoenix.menu_desfoque", prefs.globalMenuDesfoque.first())
                .putExtra("phoenix.menu_opacidade", prefs.globalMenuOpacidade.first())
                .putExtra("phoenix.menu_lado", prefs.globalMenuLado.first())
                .putExtra("phoenix.menu_tema", prefs.globalMenuTema.first())
                .putExtra("phoenix.menu_alca", prefs.globalMenuAlca.first())
                .putExtra("phoenix.menu_voltar", prefs.globalMenuVoltar.first())
                .putExtra("phoenix.menu_gesto", prefs.globalMenuGesto.first())
                .putExtra("phoenix.conq_avisos", prefs.conqAvisos.first())
                .putExtra("phoenix.conq_som", prefs.conqSom.first())
                .putExtra("phoenix.conq_vibrar", prefs.conqVibrar.first())
                .putExtra("phoenix.ra_hardcore", hardcoreFinal)
                .putExtra("phoenix.ra_hardcore_jogo", hcJogo)
                .putExtra("phoenix.conq_posicao", prefs.conqPosicao.first())
                .putExtra("phoenix.conq_duracao", prefs.conqDuracao.first())
                .putExtra("phoenix.conq_volume", prefs.conqVolume.first())
                .putExtra("phoenix.ra_usuario", com.dfdx047.phoenixemu.ra.RaCredenciais.usuario(context) ?: "")
                .putExtra("phoenix.ra_token", com.dfdx047.phoenixemu.ra.RaCredenciais.token(context) ?: "")
                .putExtra("phoenix.ra_hash", raHash)
                .putExtra("phoenix.ra_console", raConsole)

            if (rom != null) i.putExtra(EmulatorActivity.EXTRA_ROM, rom)
            if (!capa.isNullOrBlank()) i.putExtra(EmulatorActivity.EXTRA_CAPA, capa)
            runCatching { com.dfdx047.phoenixemu.ra.RaRepositorio(context).limparCache() }
            i
        }
        withContext(Dispatchers.Main) {
            context.startActivity(intent)
        }
    }

    /**
     * O mapa de teclas, na ordem que a Activity espera.
     *
     * A ordem e procurada POR NOME, nao assumida: se alguem reordenar a enum
     * BotaoVirtual ou a lista la do modulo, `valueOf` falha na hora com o
     * nome do botao, em vez de o A virar o B em silencio.
     */
    private fun mapeamento(prefs: Preferencias): IntArray {
        val mapa = prefs.mapeamento.value
        return EmulatorActivity.ORDEM_DO_MAPEAMENTO
            .map { nome -> BotaoVirtual.valueOf(nome) }
            .map { botao -> mapa[botao] ?: botao.padrao }
            .toIntArray()
    }

    /**
     * Nome-base dos saves: o nome do arquivo sem extensao, mais o CRC32 quando
     * existir. So o nome colidiria com duas ROMs iguais em pastas diferentes.
     */
    private fun nomeDeSave(jogo: Jogo): String {
        val base = jogo.nomeArquivoOriginal.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return if (jogo.crc32.isNullOrBlank()) base else "$base.${jogo.crc32}"
    }
}
