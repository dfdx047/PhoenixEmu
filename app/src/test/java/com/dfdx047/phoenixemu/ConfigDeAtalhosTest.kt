package com.dfdx047.phoenixemu

import com.dfdx047.phoenixemu.data.AcaoAtalho
import com.dfdx047.phoenixemu.data.AtalhoDaAcao
import com.dfdx047.phoenixemu.data.ConfigDeAtalhos
import com.dfdx047.phoenixemu.data.combo
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDeAtalhosTest {

    @Test
    fun padraoTemAvancarEVoltarSemHotkey() {
        val cfg = ConfigDeAtalhos.padrao()
        assertEquals(0, cfg.hotkey)
        assertEquals(1, cfg.combo(AcaoAtalho.AVANCAR).size)
        assertEquals(1, cfg.combo(AcaoAtalho.VOLTAR).size)
        assertTrue(cfg.combo(AcaoAtalho.MENU).isEmpty())
    }

    @Test
    fun comboIncluiHotkeyQuandoAtivadaEDeduplica() {
        val cfg = ConfigDeAtalhos(
            hotkey = 109,
            acoes = mapOf(
                AcaoAtalho.SALVAR_ESTADO.name to AtalhoDaAcao(tecla = 103, usarHotkey = true),
                AcaoAtalho.MENU.name to AtalhoDaAcao(tecla = 109, usarHotkey = true),
                AcaoAtalho.AVANCAR.name to AtalhoDaAcao(tecla = 105, usarHotkey = false)
            )
        )
        assertEquals(listOf(109, 103), cfg.combo(AcaoAtalho.SALVAR_ESTADO))
        assertEquals(listOf(109), cfg.combo(AcaoAtalho.MENU))
        assertEquals(listOf(105), cfg.combo(AcaoAtalho.AVANCAR))
    }

    @Test
    fun serializacaoGsonPreservaConfiguracao() {
        val original = ConfigDeAtalhos(
            hotkey = 109,
            acoes = mapOf(AcaoAtalho.REINICIAR.name to AtalhoDaAcao(tecla = 99, usarHotkey = true))
        )
        val gson = Gson()
        val restaurado = gson.fromJson(gson.toJson(original), ConfigDeAtalhos::class.java)
        assertEquals(original, restaurado)
    }
}
