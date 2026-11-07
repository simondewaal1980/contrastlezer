package nl.contrastlezer.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMotionDetectorTest {

    private val width = 128
    private val height = 96

    @Test
    fun identicalFramesGiveNoMotion() {
        val detector = FrameMotionDetector()
        val frame = checkerboard()

        detector.update(frame, width, height)
        val score = detector.update(frame, width, height)

        assertEquals(0f, score, 0.01f)
    }

    @Test
    fun shiftedFrameGivesMotion() {
        val detector = FrameMotionDetector()
        val frame = checkerboard()
        val shifted = IntArray(frame.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                shifted[y * width + x] = frame[y * width + (x + 8) % width]
            }
        }

        detector.update(frame, width, height)
        val score = detector.update(shifted, width, height)

        assertTrue("score was $score", score > 20f)
    }

    @Test
    fun globalBrightnessChangeIsNotMotion() {
        val detector = FrameMotionDetector()
        val frame = checkerboard()
        val brighter = IntArray(frame.size) { index ->
            val color = frame[index]
            val r = ((color shr 16) and 0xFF) + 30
            val g = ((color shr 8) and 0xFF) + 30
            val b = (color and 0xFF) + 30
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        detector.update(frame, width, height)
        val score = detector.update(brighter, width, height)

        assertTrue("score was $score", score < 1f)
    }

    private fun checkerboard(): IntArray {
        val dark = 0xFF505050.toInt()
        val light = 0xFFB4B4B4.toInt()
        return IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if ((x / 8 + y / 8) % 2 == 0) dark else light
        }
    }
}
