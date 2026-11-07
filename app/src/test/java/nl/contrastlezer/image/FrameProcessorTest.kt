package nl.contrastlezer.image

import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Rendert een testkaart met tekst op gekleurde vlakken door de verwerking en
 * schrijft het resultaat naar build/test-output. Zo kan de contrastomzetting
 * zonder toestel worden gecontroleerd.
 */
class FrameProcessorTest {

    @Test
    fun renderTestCard() {
        System.setProperty("java.awt.headless", "true")

        val width = 800
        val height = 1200
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.PLAIN, 22)
        g.drawString("Kleine tekst op wit papier", 24, 48)

        drawBlock(g, 90, Color(0xFFB00020.toInt()), Color.BLACK, "Rode verpakking, zwarte letters")
        drawBlock(g, 190, Color(0xFFB00020.toInt()), Color.WHITE, "Rode verpakking, witte letters")
        drawBlock(g, 290, Color(0xFF1B5E20.toInt()), Color.BLACK, "Groene verpakking, zwarte tekst")
        drawBlock(g, 390, Color(0xFFFFD500.toInt()), Color.WHITE, "Geel met witte letters")
        drawBlock(g, 490, Color(0xFF0033CC.toInt()), Color(0xFFFFD500.toInt()), "Blauw met gele letters")
        drawBlock(g, 590, Color(0xFF222222.toInt()), Color(0xFFBB2222.toInt()), "Donker met rode letters")

        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.PLAIN, 15)
        g.drawString("Ingrediënten: suiker, tarwebloem, plantaardige olie", 24, 720)
        g.drawString("(zonnebloem), cacaoboter, magere melkpoeder, emulgator", 24, 742)
        g.drawString("(sojalecithine), zout, aroma.", 24, 764)

        g.font = Font("SansSerif", Font.BOLD, 40)
        g.drawString("GROTE VETTE TEKST", 24, 850)

        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString("Zeer fijne print: 1,5 g suiker per portie (12% van de dagelijkse referentie-inname).", 24, 900)
        g.dispose()

        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val directory = File("build/test-output").apply { mkdirs() }
        ImageIO.write(image, "png", File(directory, "origineel.png"))

        for (k in listOf(0f, 0.8f, 1.1f, 1.5f)) {
            val copy = pixels.copyOf()
            FrameProcessor.process(
                pixels = copy,
                width = width,
                height = height,
                palette = HighContrastPalettes[0],
                threshold = 33f,
                radiusFraction = 0.03f,
                stdFactor = k,
            )
            val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            output.setRGB(0, 0, width, height, copy, 0, width)
            ImageIO.write(
                output,
                "png",
                File(directory, "verwerkt_k${(k * 10).toInt()}.png"),
            )
        }
    }

    private fun drawBlock(
        g: java.awt.Graphics2D,
        top: Int,
        background: Color,
        foreground: Color,
        text: String,
    ) {
        g.color = background
        g.fillRect(16, top, 768, 76)
        g.color = foreground
        g.font = Font("SansSerif", Font.PLAIN, 30)
        g.drawString(text, 32, top + 50)
    }
}
