package com.dfdx047.phoenixemu

import org.junit.Assert.assertEquals
import org.junit.Test

class RomParserTest {

    @Test
    fun analisaNomeNoIntroMultiRegiao() {
        val r = RomParser.analisar("Chrono Trigger (USA, Europe) (Rev 1) [!]")
        assertEquals("Chrono Trigger", r.nomeLimpo)
        assertEquals("USA/EUR", r.regiao)
        assertEquals("Chrono Trigger (USA, Europe) (Rev 1)", r.nomeLibretro)
    }

    @Test
    fun ignoraIdiomasEExtraiRegiaoReal() {
        val r = RomParser.analisar("Donkey Kong Country (En,Fr,De) (Europe)")
        assertEquals("Donkey Kong Country", r.nomeLimpo)
        assertEquals("EUR", r.regiao)
    }

    @Test
    fun reconheceCodigoGoodNes() {
        val r = RomParser.analisar("Contra (U) [!]")
        assertEquals("Contra", r.nomeLimpo)
        assertEquals("USA", r.regiao)
        assertEquals("Contra (U)", r.nomeLibretro)
    }

    @Test
    fun semRegiaoDevolveDesconhecida() {
        val r = RomParser.analisar("Homebrew Game [T+Por]")
        assertEquals("Homebrew Game", r.nomeLimpo)
        assertEquals(Jogo.REGIAO_DESCONHECIDA, r.regiao)
        assertEquals("Homebrew Game", r.nomeLibretro)
    }

    @Test
    fun limitaDoisCodigosDeRegiao() {
        val r = RomParser.analisar("Super Game (USA, Europe, Japan)")
        assertEquals("USA/EUR", r.regiao)
    }
}
