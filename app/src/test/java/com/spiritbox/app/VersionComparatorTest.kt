package com.spiritbox.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `isNewer recusa sufixos e tags inválidas`() {
        assertFalse(VersionComparator.isNewer("banana", "1.0"))
        assertFalse(VersionComparator.isNewer("v1.0", "1.0"))
        assertFalse(VersionComparator.isNewer("", "1.0"))
        assertFalse(VersionComparator.isNewer(null, "1.0"))
    }

    @Test
    fun `isNewer aceita tags v prefixadas`() {
        assertTrue(VersionComparator.isNewer("v1.0.1", "1.0"))
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun `isNewer trata numeros maiores corretamente`() {
        assertFalse(VersionComparator.isNewer("v2.0", "2.0"))
        assertFalse(VersionComparator.isNewer("v0.9.9", "1.0"))
        assertTrue(VersionComparator.isNewer("v1.1", "1.0.9"))
    }
}
