package nl.contrastlezer.image

import kotlin.math.abs

/**
 * Meet hoe sterk het beeld ten opzichte van het vorige frame is veranderd.
 *
 * Van elk frame wordt een klein grayscale-rooster gemaakt (elke
 * [sampleStep] pixels). Het gemiddelde absolute verschil tussen twee roosters
 * is de bewegingsscore in 0..255. Een algehele helderheidsverandering (zoals
 * de camera die zich aanpast) wordt eruit gefilterd, zodat die niet als
 * beweging telt.
 *
 * De klasse is pure Kotlin, zodat hij los van Android getest kan worden.
 */
class FrameMotionDetector(private val sampleStep: Int = 8) {

    private var previous = IntArray(0)
    private var current = IntArray(0)
    private var columns = 0
    private var rows = 0

    /**
     * Berekent de bewegingsscore ten opzichte van het vorige frame.
     * Het eerste frame (of een frame met andere afmetingen) levert 0 op.
     */
    fun update(pixels: IntArray, width: Int, height: Int): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return 0f

        val newColumns = (width + sampleStep - 1) / sampleStep
        val newRows = (height + sampleStep - 1) / sampleStep
        val size = newColumns * newRows

        if (newColumns != columns || newRows != rows || previous.size != size) {
            columns = newColumns
            rows = newRows
            previous = IntArray(size)
            current = IntArray(size)
            capture(pixels, width, height)
            swapBuffers()
            return 0f
        }

        capture(pixels, width, height)

        var sumAbsolute = 0L
        var sumSigned = 0L
        for (i in 0 until size) {
            val difference = current[i] - previous[i]
            sumAbsolute += abs(difference)
            sumSigned += difference
        }
        swapBuffers()

        // Trek de gemiddelde (globale) helderheidsverandering af: die is geen
        // beweging van het onderwerp.
        val movement = sumAbsolute - abs(sumSigned)
        return (movement.toFloat() / size).coerceIn(0f, 255f)
    }

    fun reset() {
        previous = IntArray(0)
        current = IntArray(0)
        columns = 0
        rows = 0
    }

    private fun capture(pixels: IntArray, width: Int, height: Int) {
        var index = 0
        for (row in 0 until rows) {
            val y = row * sampleStep
            val rowBase = y * width
            for (column in 0 until columns) {
                val x = column * sampleStep
                val color = pixels[rowBase + x]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                current[index++] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
    }

    private fun swapBuffers() {
        val temporary = previous
        previous = current
        current = temporary
    }
}
