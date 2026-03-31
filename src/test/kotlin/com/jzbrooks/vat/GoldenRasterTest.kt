package com.jzbrooks.vat

import com.jzbrooks.vgo.util.parse
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.MediaType
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
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

        assertArrayEquals(goldenBytes, actualBytes, "$testName: rendered output does not match golden image")
    }

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
