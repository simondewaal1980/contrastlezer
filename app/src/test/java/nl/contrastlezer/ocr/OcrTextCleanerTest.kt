package nl.contrastlezer.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextCleanerTest {

    @Test
    fun `haalt spaties aan de randen van elke regel weg`() {
        val raw = "  Ingrediënten: suiker  \n\t tarwebloem \n"

        assertEquals(
            "Ingrediënten: suiker\ntarwebloem",
            OcrTextCleaner.clean(raw),
        )
    }

    @Test
    fun `voegt opeenvolgende lege regels samen`() {
        val raw = "Eerste alinea\n\n\n\nTweede alinea"

        assertEquals(
            "Eerste alinea\n\nTweede alinea",
            OcrTextCleaner.clean(raw),
        )
    }

    @Test
    fun `normaliseert windows- en oude mac-regeleinden`() {
        assertEquals("een\ntwee\ndrie", OcrTextCleaner.clean("een\r\ntwee\r drie"))
    }

    @Test
    fun `laat geen lege regels aan begin en eind achter`() {
        assertEquals("tekst", OcrTextCleaner.clean("\n\n  tekst  \n\n"))
    }

    @Test
    fun `lege invoer blijft leeg`() {
        assertEquals("", OcrTextCleaner.clean(""))
        assertEquals("", OcrTextCleaner.clean("   \n\n\t"))
    }
}
