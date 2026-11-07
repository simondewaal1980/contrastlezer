package nl.contrastlezer.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextSplitterTest {

    @Test
    fun `korte tekst blijft één stuk`() {
        assertEquals(listOf("Korte tekst."), SpeechTextSplitter.split("Korte tekst.", 100))
    }

    @Test
    fun `knipt bij voorkeur na een zinseinde`() {
        val text = "Eerste zin. Tweede zin."

        assertEquals(
            listOf("Eerste zin.", "Tweede zin."),
            SpeechTextSplitter.split(text, 16),
        )
    }

    @Test
    fun `knipt anders bij een spatie`() {
        val text = "een twee drie vier vijf"

        assertEquals(
            listOf("een twee drie", "vier vijf"),
            SpeechTextSplitter.split(text, 16),
        )
    }

    @Test
    fun `knipt een woord zonder spaties hard`() {
        assertEquals(
            listOf("abcdefgh", "ij"),
            SpeechTextSplitter.split("abcdefghij", 8),
        )
    }

    @Test
    fun `laat lege stukken weg`() {
        assertEquals(listOf("tekst"), SpeechTextSplitter.split("   tekst   ", 100))
    }
}
