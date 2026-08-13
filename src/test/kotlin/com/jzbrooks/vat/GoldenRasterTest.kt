package com.jzbrooks.vat

import com.jzbrooks.vgo.util.parse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.MediaType
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.writeBytes

class GoldenRasterTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenTestCases")
    fun renderedOutputMatchesGolden(testName: String, inputPath: String, goldenPath: String, reporter: TestReporter) {
        val inputFile = File(javaClass.classLoader.getResource(inputPath)!!.toURI())
        val image = parse(inputFile) ?: fail("Failed to parse $inputPath")

        val actualBytes = renderToPng(image)

        reporter.publishFile("$testName-actual.png", MediaType.IMAGE_PNG) { it.writeBytes(actualBytes) }

        if (System.getProperty("vat.updateGoldens")?.isNotBlank() == true) {
            val goldenFile = File("src/test/resources/$goldenPath")
            goldenFile.parentFile.mkdirs()
            goldenFile.writeBytes(actualBytes)
            return
        }

        val goldenUrl = javaClass.classLoader.getResource(goldenPath)
        assertNotNull(goldenUrl, "Golden file missing: $goldenPath. Run with -Dvat.updateGoldens=true to generate.")
        val goldenBytes = goldenUrl!!.readBytes()

        reporter.publishFile("$testName-golden.png", MediaType.IMAGE_PNG) { it.writeBytes(goldenBytes) }

        assertPixelsEqual(testName, goldenBytes, actualBytes)
    }

    // Compare decoded pixels rather than encoded bytes. The same raster encodes to different
    // png bytes across skia versions, which says nothing about what vat drew.
    private fun assertPixelsEqual(testName: String, goldenBytes: ByteArray, actualBytes: ByteArray) {
        val golden = decode(goldenBytes) ?: fail("$testName: unable to decode the golden image")
        val actual = decode(actualBytes) ?: fail("$testName: unable to decode the rendered image")

        if (golden.width != actual.width || golden.height != actual.height) {
            fail<Unit>(
                "$testName: rendered image is ${actual.width}x${actual.height}, " +
                    "but the golden image is ${golden.width}x${golden.height}",
            )
        }

        var differing = 0
        var firstDifference: String? = null
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                val goldenPixel = golden.getRGB(x, y)
                val actualPixel = actual.getRGB(x, y)
                if (goldenPixel != actualPixel) {
                    differing++
                    if (firstDifference == null) {
                        firstDifference = "($x, $y), where the golden image is " +
                            "%08x and the rendered image is %08x".format(goldenPixel, actualPixel)
                    }
                }
            }
        }

        if (differing > 0) {
            val total = golden.width * golden.height
            fail<Unit>("$testName: $differing of $total pixels differ, beginning at $firstDifference")
        }
    }

    private fun decode(bytes: ByteArray): BufferedImage? = ImageIO.read(ByteArrayInputStream(bytes))

    companion object {
        @JvmStatic
        fun goldenTestCases(): List<Arguments> {
            val cases = mutableListOf<Arguments>()
            for (dir in listOf("input/svg", "input/vd")) {
                val resource = GoldenRasterTest::class.java.classLoader.getResource(dir) ?: continue
                File(resource.toURI()).walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val relativePath = "$dir/${file.name}"
                        val goldenPath = relativePath
                            .replace("input/", "golden/")
                            .replaceAfterLast('.', "png")
                        cases.add(Arguments.of(file.nameWithoutExtension, relativePath, goldenPath))
                    }
            }
            return cases
        }
    }
}
