package com.spiritbox.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesCodecTest {

    @Test
    fun `export gera json com os numeros certos`() {
        assertEquals(
            "{\"format\":\"spiritbox-favorites\",\"version\":1,\"favorites\":[830,1760,25000]}",
            FavoritesCodec.export(listOf(830.0, 1760.0, 25000.0))
        )
    }

    @Test
    fun `export de lista vazia gera array vazio`() {
        assertEquals(
            "{\"format\":\"spiritbox-favorites\",\"version\":1,\"favorites\":[]}",
            FavoritesCodec.export(emptyList())
        )
    }

    @Test
    fun `parse le o arquivo exportado`() {
        val out = FavoritesCodec.export(listOf(830.0, 1150.5, 25000.0))
        assertEquals(listOf(830.0, 1150.5, 25000.0), FavoritesCodec.parse(out))
    }

    @Test
    fun `parse tolera espacos e quebras de linha`() {
        val text = """
            {"format":"spiritbox-favorites","version":1,
              "favorites":[
                830, 1760,
                25000
              ]}
        """.trimIndent()
        assertEquals(listOf(830.0, 1760.0, 25000.0), FavoritesCodec.parse(text))
    }

    @Test
    fun `parse aceita lista simples de numeros`() {
        assertEquals(listOf(830.0, 1760.0), FavoritesCodec.parse("[830, 1760]"))
    }

    @Test
    fun `parse descarta numeros invalidos`() {
        assertEquals(listOf(830.0, 25000.0), FavoritesCodec.parse("[830, banana, 25000]"))
    }

    @Test
    fun `parse de texto que nao e favoritos devolve null`() {
        assertNull(FavoritesCodec.parse("isto nao e um arquivo de favoritos"))
    }

    @Test
    fun `parse de arquivo valido sem frequencias nao devolve null`() {
        assertEquals(emptyList<Double>(), FavoritesCodec.parse("{\"favorites\":[]}"))
    }

    @Test
    fun `merge adiciona apenas frequencias novas`() {
        val result = FavoritesCodec.merge(
            listOf(830.0, 1760.0, 830.5),
            listOf(830.0, 25000.0)
        )
        assertEquals(listOf(830.0, 1760.0, 25000.0), result.favorites)
        assertEquals(1, result.added)
    }

    @Test
    fun `merge ordena e remove repetidos dentro da tolerancia`() {
        val result = FavoritesCodec.merge(
            listOf(25000.0, 830.0, 1760.4, 1760.6),
            emptyList()
        )
        assertEquals(listOf(830.0, 1760.4, 25000.0), result.favorites)
        assertEquals(3, result.added)
    }

    @Test
    fun `merge ignora zero e negativo`() {
        val result = FavoritesCodec.merge(listOf(0.0, -5.0, 830.0), emptyList())
        assertEquals(listOf(830.0), result.favorites)
        assertEquals(1, result.added)
    }
}
