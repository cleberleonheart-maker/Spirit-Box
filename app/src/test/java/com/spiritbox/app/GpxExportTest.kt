package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxExportTest {

    @Test
    fun `gera cabecalho gpX valido`() {
        val out = GpxExport.build(emptyList())
        assertTrue(out.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(out.contains("version=\"1.1\""))
        assertTrue(out.contains("<trk>"))
        assertTrue(out.contains("<trkseg>"))
        assertTrue(out.endsWith("</gpx>\n"))
    }

    @Test
    fun `cada ponto vira um trkpt com hora e mg`() {
        val out = GpxExport.build(
            listOf(
                GpxExport.Point(-23.55052, -46.633308, 12.5f, 1700000000000L),
                GpxExport.Point(10.0, 20.0, 0f, 1700000060000L)
            )
        )
        assertEquals(2, Regex("<trkpt ").findAll(out).count())
        assertTrue(out.contains("lat=\"-23.55052\" lon=\"-46.633308\""))
        assertTrue(out.contains("<time>2023-11-14T22:13:20Z</time>"))
        assertTrue(out.contains("<mg>12.5</mg>"))
        assertTrue(out.contains("<mg>0</mg>"))
    }

    @Test
    fun `nome com caracteres especiais e escapado`() {
        val out = GpxExport.build(emptyList(), name = "A & B <trajeto>")
        assertTrue(out.contains("<name>A &amp; B &lt;trajeto&gt;</name>"))
    }

    @Test
    fun `formata coordenadas sem zeros a direita`() {
        assertEquals("-23.55052", GpxExport.fmtNum(-23.550520))
        assertEquals("10", GpxExport.fmtNum(10.0))
        assertEquals("0.000001", GpxExport.fmtNum(0.000001))
    }
}
