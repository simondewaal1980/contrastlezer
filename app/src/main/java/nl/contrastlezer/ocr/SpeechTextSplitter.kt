package nl.contrastlezer.ocr

/**
 * Splitst lange tekst in stukken die de tekst-naar-spraak-engine aankan.
 * Er wordt bij voorkeur op een zinseinde of spatie geknipt, zodat woorden
 * heel blijven. Zonder Android-klassen, dus op de gewone JVM te testen.
 */
internal object SpeechTextSplitter {

    fun split(text: String, maxLength: Int): List<String> {
        require(maxLength > 0) { "maxLength moet groter dan nul zijn" }
        if (text.length <= maxLength) {
            val trimmed = text.trim()
            return if (trimmed.isEmpty()) emptyList() else listOf(trimmed)
        }

        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxLength, text.length)
            if (end < text.length) {
                val boundary = findBoundary(text, start, end)
                if (boundary > start) end = boundary
            }
            val part = text.substring(start, end).trim()
            if (part.isNotEmpty()) parts += part
            start = end
        }
        return parts
    }

    /** Zoekt vanaf [end] terug naar een zinseinde of anders een spatie. */
    private fun findBoundary(text: String, start: Int, end: Int): Int {
        for (i in end - 1 downTo start + 1) {
            if (text[i] in SENTENCE_ENDINGS && i + 1 < text.length && text[i + 1].isWhitespace()) {
                return i + 1
            }
        }
        for (i in end - 1 downTo start + 1) {
            if (text[i].isWhitespace()) return i + 1
        }
        return end
    }

    private val SENTENCE_ENDINGS = charArrayOf('.', '!', '?', ';', ':')
}
