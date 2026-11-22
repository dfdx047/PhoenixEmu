package com.dfdx047.phoenixemu

import androidx.compose.ui.input.key.Key
import com.dfdx047.phoenixemu.ui.design.NavegacaoDeLista
import com.dfdx047.phoenixemu.ui.design.NavegacaoDeLista.Acao
import org.junit.Assert.assertEquals
import org.junit.Test

class NavegacaoDeListaTest {

    @Test
    fun gradeMoveNasQuatroDirecoesERespeitaLimites() {
        val rDir = NavegacaoDeLista.interpretarTecla(Key.DirectionRight, indice = 0, total = 10, colunas = 4, emGrade = true)
        assertEquals(Acao.MOVER, rDir.acao)
        assertEquals(1, rDir.novoIndice)

        val rEsqLimite = NavegacaoDeLista.interpretarTecla(Key.DirectionLeft, indice = 0, total = 10, colunas = 4, emGrade = true)
        assertEquals(Acao.LIMITE, rEsqLimite.acao)

        val rBaixo = NavegacaoDeLista.interpretarTecla(Key.DirectionDown, indice = 1, total = 10, colunas = 4, emGrade = true)
        assertEquals(Acao.MOVER, rBaixo.acao)
        assertEquals(5, rBaixo.novoIndice)

        val rCimaSair = NavegacaoDeLista.interpretarTecla(Key.DirectionUp, indice = 2, total = 10, colunas = 4, emGrade = true)
        assertEquals(Acao.SAIR_PARA_CIMA, rCimaSair.acao)

        val rBaixoSair = NavegacaoDeLista.interpretarTecla(Key.DirectionDown, indice = 9, total = 10, colunas = 4, emGrade = true)
        assertEquals(Acao.SAIR_PARA_BAIXO, rBaixoSair.acao)
    }

    @Test
    fun xmbSobeESaiParaCimaDesceESaiParaBaixo() {
        val rCima = NavegacaoDeLista.interpretarTecla(Key.DirectionUp, indice = 3, total = 8, colunas = 4, emGrade = false)
        assertEquals(Acao.SAIR_PARA_CIMA, rCima.acao)

        val rBaixo = NavegacaoDeLista.interpretarTecla(Key.DirectionDown, indice = 3, total = 8, colunas = 4, emGrade = false)
        assertEquals(Acao.SAIR_PARA_BAIXO, rBaixo.acao)
    }

    @Test
    fun confirmaEAbreOpcoes() {
        val rAbrir = NavegacaoDeLista.interpretarTecla(Key.ButtonA, indice = 4, total = 8, colunas = 4, emGrade = true)
        assertEquals(Acao.ABRIR, rAbrir.acao)
        assertEquals(4, rAbrir.novoIndice)

        val rOpcoes = NavegacaoDeLista.interpretarTecla(Key.ButtonY, indice = 4, total = 8, colunas = 4, emGrade = true)
        assertEquals(Acao.OPCOES, rOpcoes.acao)
        assertEquals(4, rOpcoes.novoIndice)
    }

    @Test
    fun listaVaziaDevolveNada() {
        val r = NavegacaoDeLista.interpretarTecla(Key.DirectionRight, indice = 0, total = 0, colunas = 4, emGrade = true)
        assertEquals(Acao.NADA, r.acao)
    }
}
