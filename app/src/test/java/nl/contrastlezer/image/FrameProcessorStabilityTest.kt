package nl.contrastlezer.image

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Controleert dat de temporele hysterese pixels vasthoudt en zo flikkeren
 * tussen tekst en achtergrond voorkomt.
 */
class FrameProcessorStabilityTest {

    private val width = 64
    private val height = 64
    private val center = (height / 2) * width + width / 2
    private val palette = HighContrastPalettes[0]

    @Test
    fun foregroundStaysForegroundWhenDifferenceDropsSlightly() {
        val first = whiteFrame()
        first[center] = BLACK
        val mask = IntArray(width * height) { -1 }

        process(first, mask, threshold = 60f, hysteresis = 30f)
        assertEquals(palette.foreground, first[center])

        // Deze pixel wijkt nog maar ~55 af van de achtergrond. Zonder
        // hysterese is dat te weinig (drempel 60), met hysterese blijft hij
        // staan omdat hij al tekst was.
        val next = whiteFrame()
        next[center] = 0xFFC8C8C8.toInt()
        process(next, mask.copyOf(), threshold = 60f, hysteresis = 30f)
        assertEquals(palette.foreground, next[center])

        val withoutHysteresis = whiteFrame()
        withoutHysteresis[center] = 0xFFC8C8C8.toInt()
        process(withoutHysteresis, null, threshold = 60f, hysteresis = 0f)
        assertEquals(palette.background, withoutHysteresis[center])
    }

    @Test
    fun backgroundStaysBackgroundWhenDifferenceIsJustTooSmall() {
        val first = whiteFrame()
        val mask = IntArray(width * height) { -1 }
        process(first, mask, threshold = 30f, hysteresis = 20f)

        // ~40 verschil: zonder hysterese net tekst, met hysterese nog niet.
        val next = whiteFrame()
        next[center] = 0xFFD7D7D7.toInt()
        process(next, mask.copyOf(), threshold = 30f, hysteresis = 20f)
        assertEquals(palette.background, next[center])

        val withoutHysteresis = whiteFrame()
        withoutHysteresis[center] = 0xFFD7D7D7.toInt()
        process(withoutHysteresis, null, threshold = 30f, hysteresis = 0f)
        assertEquals(palette.foreground, withoutHysteresis[center])
    }

    private fun process(
        pixels: IntArray,
        mask: IntArray?,
        threshold: Float,
        hysteresis: Float,
    ) {
        FrameProcessor.process(
            pixels = pixels,
            width = width,
            height = height,
            palette = palette,
            threshold = threshold,
            radiusFraction = 0.2f,
            stdFactor = 0f,
            previousMask = mask,
            hysteresis = hysteresis,
        )
    }

    private fun whiteFrame(): IntArray = IntArray(width * height) { WHITE }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
