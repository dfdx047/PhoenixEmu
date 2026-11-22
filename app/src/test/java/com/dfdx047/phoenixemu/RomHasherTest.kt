package com.dfdx047.phoenixemu

import com.dfdx047.phoenixemu.data.RomHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class RomHasherTest {

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun nesIgnoraCabecalhoInesNoHashRa() {
        val payload = ByteArray(256) { (it and 0xFF).toByte() }
        val inesHeader = byteArrayOf(0x4E, 0x45, 0x53, 0x1A) + ByteArray(12) { 0x01 }
        val comCabecalho = inesHeader + payload

        val idCom = RomHasher.umaPassada(ByteArrayInputStream(comCabecalho), Sistema.NES)
        val idSem = RomHasher.umaPassada(ByteArrayInputStream(payload), Sistema.NES)

        assertNotNull(idCom)
        assertNotNull(idSem)
        assertEquals(md5Hex(payload), idCom!!.hashRa)
        assertEquals(idSem!!.hashRa, idCom.hashRa)
        assertNotEquals(idSem.crc32, idCom.crc32)
    }

    @Test
    fun snesIgnoraCabecalhoCopiador512NoHashRa() {
        val payload = ByteArray(2048) { ((it * 7) and 0xFF).toByte() }
        val copierHeader = ByteArray(512) { 0xAA.toByte() }
        val comCabecalho = copierHeader + payload

        val idCom = RomHasher.umaPassada(ByteArrayInputStream(comCabecalho), Sistema.SNES)
        val idSem = RomHasher.umaPassada(ByteArrayInputStream(payload), Sistema.SNES)

        assertNotNull(idCom)
        assertNotNull(idSem)
        assertEquals(md5Hex(payload), idCom!!.hashRa)
        assertEquals(idSem!!.hashRa, idCom.hashRa)
        assertEquals(2560L, idCom.bytesLidos)
    }

    @Test
    fun streamVazioRetornaNull() {
        val id = RomHasher.umaPassada(ByteArrayInputStream(ByteArray(0)), Sistema.NES)
        assertNull(id)
    }
}
