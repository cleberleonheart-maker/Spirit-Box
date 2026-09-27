package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorLeaseTest {

    @Test
    fun `primeiro acquire e quem registra`() {
        val lease = SensorLease()
        assertTrue(lease.acquire())
        assertTrue(lease.isHeld)
    }

    @Test
    fun `segundo acquire nao registra de novo`() {
        val lease = SensorLease()
        lease.acquire()
        assertFalse(lease.acquire())
        assertEquals(2, lease.count)
    }

    @Test
    fun `release atrasado nao desregistra enquanto outro dono segura`() {
        val lease = SensorLease()
        lease.acquire()
        lease.acquire()
        // o dismiss do primeiro dono chega depois do acquire do segundo
        assertFalse(lease.release())
        assertTrue(lease.isHeld)
    }

    @Test
    fun `so o ultimo release desregistra`() {
        val lease = SensorLease()
        lease.acquire()
        lease.acquire()
        assertFalse(lease.release())
        assertTrue(lease.release())
        assertFalse(lease.isHeld)
    }

    @Test
    fun `release sem dono nao fica negativo`() {
        val lease = SensorLease()
        assertFalse(lease.release())
        assertEquals(0, lease.count)
    }

    @Test
    fun `releaseAll descarta referencias pendentes`() {
        val lease = SensorLease()
        lease.acquire()
        lease.acquire()
        assertEquals(2, lease.releaseAll())
        assertFalse(lease.isHeld)
    }

    @Test
    fun `releaseAll depois libera referencia pendente nao derruba o estado`() {
        val lease = SensorLease()
        lease.acquire()
        lease.releaseAll()
        assertFalse(lease.release())
        assertFalse(lease.isHeld)
    }
}
