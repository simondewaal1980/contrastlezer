package nl.contrastlezer.image

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Zet een camerabeeld om naar twee kleuren met maximaal contrast.
 *
 * Per pixel wordt een lokaal gemiddelde berekend (box-filter via
 * integraalbeelden). Alles wat duidelijk afwijkt van dat gemiddelde — in
 * helderheid én in kleur — wordt "tekst" en krijgt de voorgrondkleur; de rest
 * krijgt de achtergrondkleur. Zo verdwijnen kleuren van bijvoorbeeld een
 * verpakking en blijft alleen het vormverschil van de letters over.
 *
 * De verwerking is bewust pure Kotlin (geen Bitmap-afhankelijkheid) zodat hij
 * los getest kan worden.
 */
object FrameProcessor {

    /** Grootte van de blokken waarin het gemiddelde wordt berekend. */
    private const val BLOCK = 4

    /** Meetstraal als fractie van de beeldbreedte. */
    private const val RADIUS_FRACTION = 0.015f

    /**
     * Verwerkt [pixels] (ARGB) in-place.
     *
     * @param threshold drempel in 0..255: hoe hoger, hoe minder snel iets
     *   als tekst wordt gezien.
     * @param radiusFraction meetstraal als fractie van de beeldbreedte.
     * @param stdFactor hoe sterk de drempel meeschaalt met het lokale contrast.
     */
    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        palette: Palette,
        threshold: Float,
        radiusFraction: Float = RADIUS_FRACTION,
        stdFactor: Float = 1.1f,
    ) {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return

        val blockWidth = (width + BLOCK - 1) / BLOCK
        val blockHeight = (height + BLOCK - 1) / BLOCK
        val blockCount = blockWidth * blockHeight

        val count = IntArray(blockCount)
        val sumR = IntArray(blockCount)
        val sumG = IntArray(blockCount)
        val sumB = IntArray(blockCount)
        val sumLum = IntArray(blockCount)
        val sumLumSquared = IntArray(blockCount)

        var index = 0
        for (y in 0 until height) {
            val rowBase = (y / BLOCK) * blockWidth
            for (x in 0 until width) {
                val color = pixels[index]
                val block = rowBase + x / BLOCK
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                sumR[block] += r
                sumG[block] += g
                sumB[block] += b
                sumLum[block] += lum
                sumLumSquared[block] += lum * lum
                count[block]++
                index++
            }
        }

        for (i in 0 until blockCount) {
            val n = count[i]
            if (n > 0) {
                sumR[i] /= n
                sumG[i] /= n
                sumB[i] /= n
                sumLum[i] /= n
                sumLumSquared[i] /= n
            }
        }

        // Integraalbeelden van de blokgemiddelden.
        val integralWidth = blockWidth + 1
        val integralR = IntArray(integralWidth * (blockHeight + 1))
        val integralG = IntArray(integralWidth * (blockHeight + 1))
        val integralB = IntArray(integralWidth * (blockHeight + 1))
        val integralLumSquared = IntArray(integralWidth * (blockHeight + 1))

        for (by in 0 until blockHeight) {
            for (bx in 0 until blockWidth) {
                val src = by * blockWidth + bx
                val dst = (by + 1) * integralWidth + (bx + 1)

                integralR[dst] = sumR[src] + integralR[dst - 1] +
                    integralR[dst - integralWidth] - integralR[dst - integralWidth - 1]
                integralG[dst] = sumG[src] + integralG[dst - 1] +
                    integralG[dst - integralWidth] - integralG[dst - integralWidth - 1]
                integralB[dst] = sumB[src] + integralB[dst - 1] +
                    integralB[dst - integralWidth] - integralB[dst - integralWidth - 1]
                integralLumSquared[dst] = sumLumSquared[src] + integralLumSquared[dst - 1] +
                    integralLumSquared[dst - integralWidth] -
                    integralLumSquared[dst - integralWidth - 1]
            }
        }

        val radiusBlocks = max(1, (width * radiusFraction / BLOCK).roundToInt())

        // Box-gemiddelde per blok.
        val meanR = IntArray(blockCount)
        val meanG = IntArray(blockCount)
        val meanB = IntArray(blockCount)
        val meanLumSquared = IntArray(blockCount)

        for (by in 0 until blockHeight) {
            for (bx in 0 until blockWidth) {
                val x0 = max(0, bx - radiusBlocks)
                val x1 = min(blockWidth - 1, bx + radiusBlocks)
                val y0 = max(0, by - radiusBlocks)
                val y1 = min(blockHeight - 1, by + radiusBlocks)

                val a = y0 * integralWidth + x0
                val b = y0 * integralWidth + (x1 + 1)
                val c = (y1 + 1) * integralWidth + x0
                val d = (y1 + 1) * integralWidth + (x1 + 1)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val dst = by * blockWidth + bx

                meanR[dst] = (integralR[d] - integralR[b] - integralR[c] + integralR[a]) / area
                meanG[dst] = (integralG[d] - integralG[b] - integralG[c] + integralG[a]) / area
                meanB[dst] = (integralB[d] - integralB[b] - integralB[c] + integralB[a]) / area
                meanLumSquared[dst] =
                    (integralLumSquared[d] - integralLumSquared[b] -
                        integralLumSquared[c] + integralLumSquared[a]) / area
            }
        }

        val foreground = palette.foreground
        val background = palette.background

        index = 0
        for (y in 0 until height) {
            // Bilineair de smooth gemiddelden bemonsteren.
            val gy = (y + 0.5f) / BLOCK - 0.5f
            val y0 = floor(gy).toInt().coerceIn(0, blockHeight - 1)
            val y1 = (y0 + 1).coerceAtMost(blockHeight - 1)
            val ty = (gy - y0).coerceIn(0f, 1f)
            val rowA = y0 * blockWidth
            val rowB = y1 * blockWidth

            for (x in 0 until width) {
                val gx = (x + 0.5f) / BLOCK - 0.5f
                val x0 = floor(gx).toInt().coerceIn(0, blockWidth - 1)
                val x1 = (x0 + 1).coerceAtMost(blockWidth - 1)
                val tx = (gx - x0).coerceIn(0f, 1f)

                val topR = meanR[rowA + x0] + (meanR[rowA + x1] - meanR[rowA + x0]) * tx
                val bottomR = meanR[rowB + x0] + (meanR[rowB + x1] - meanR[rowB + x0]) * tx
                val localR = topR + (bottomR - topR) * ty

                val topG = meanG[rowA + x0] + (meanG[rowA + x1] - meanG[rowA + x0]) * tx
                val bottomG = meanG[rowB + x0] + (meanG[rowB + x1] - meanG[rowB + x0]) * tx
                val localG = topG + (bottomG - topG) * ty

                val topB = meanB[rowA + x0] + (meanB[rowA + x1] - meanB[rowA + x0]) * tx
                val bottomB = meanB[rowB + x0] + (meanB[rowB + x1] - meanB[rowB + x0]) * tx
                val localB = topB + (bottomB - topB) * ty

                val topSq = meanLumSquared[rowA + x0] +
                    (meanLumSquared[rowA + x1] - meanLumSquared[rowA + x0]) * tx
                val bottomSq = meanLumSquared[rowB + x0] +
                    (meanLumSquared[rowB + x1] - meanLumSquared[rowB + x0]) * tx
                val localSquared = topSq + (bottomSq - topSq) * ty

                val color = pixels[index]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val luminance = (r * 299 + g * 587 + b * 114) / 1000
                val localLuminance = (localR * 299 + localG * 587 + localB * 114) / 1000
                val luminanceDiff = abs(luminance - localLuminance)

                val chromaDiff = max(
                    abs(r - localR),
                    max(abs(g - localG), abs(b - localB)),
                )

                val variance = max(0f, localSquared - localLuminance * localLuminance)
                val localContrast = sqrt(variance)
                val adaptiveThreshold = max(threshold, stdFactor * localContrast)

                val difference = max(luminanceDiff.toFloat(), chromaDiff * 0.6f)
                pixels[index] = if (difference >= adaptiveThreshold) foreground else background
                index++
            }
        }
    }
}
