package nl.contrastlezer.ocr

/**
 * Maakt ruwe OCR-tekst geschikt voor weergave: regeleinden worden
 * genormaliseerd, spaties aan het begin en eind van elke regel verdwijnen en
 * opeenvolgende lege regels worden samengevoegd tot één lege regel.
 *
 * Bewust zonder Android-klassen, zodat dit op de gewone JVM te testen is.
 */
object OcrTextCleaner {

    fun clean(raw: String): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trim() }

        val result = mutableListOf<String>()
        for (line in lines) {
            val previous = result.lastOrNull()
            if (line.isEmpty() && (previous == null || previous.isEmpty())) continue
            result += line
        }
        while (result.lastOrNull()?.isEmpty() == true) {
            result.removeAt(result.lastIndex)
        }
        return result.joinToString("\n")
    }
}
