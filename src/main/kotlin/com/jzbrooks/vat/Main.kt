package com.jzbrooks.vat

import com.jzbrooks.vgo.svg.ScalableVectorGraphic
import com.jzbrooks.vgo.vd.VectorDrawable
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import com.jzbrooks.vgo.svg.parse as svgParse
import com.jzbrooks.vgo.vd.parse as vdParse

private const val HELP_MESSAGE = """
> vat [options] [file]

vat renders vector artwork (SVG & Android Vector Drawable) to the terminal.

Options:
  -s --scale      scale factor (float or integer)
  -h --help       print this message
  -v --version    print the version number
"""

@OptIn(ExperimentalEncodingApi::class)
fun main(args: Array<String>) {
    val argReader = ArgReader(args.toMutableList())
    val printHelp = argReader.readFlag("help|h")
    if (printHelp) {
        println(HELP_MESSAGE)
        exitProcess(0)
        return
    }

    val printVersion = argReader.readFlag("version|v")
    if (printVersion) {
        println(BuildConstants.VERSION)
        exitProcess(0)
        return
    }

    val scale = argReader.readOption("s|scale")?.let {
        val factor = it.toFloatOrNull()
        if (factor != null) {
            factor
        } else {
            System.err.println("$it is not a floating point value")
            1f
        }
    } ?: 1f

    val path = argReader.readArguments().first()
    val image =
        File(path).inputStream().use { inputStream ->
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()

            val document = documentBuilderFactory.newDocumentBuilder().parse(inputStream)
            document.documentElement.normalize()

            when (document.documentElement.tagName) {
                "vector" -> vdParse(document.documentElement)
                "svg" -> svgParse(document.documentElement)
                else -> null
            }
        }

    if (image == null) {
        System.err.println("Unable to load image $path")
        exitProcess(-1)
    }

    val (width, height) = when (image) {
        is VectorDrawable -> {
            val width = image.foreign["android:width"]!!.filter(Char::isDigit).toInt()
            val height = image.foreign["android:height"]!!.filter(Char::isDigit).toInt()
            Pair(width, height)
        }
        is ScalableVectorGraphic -> {
            val viewBox = image.foreign["viewBox"]!!.split("[\\s,]+".toRegex()).map { it.filter(Char::isDigit).toInt() }
            Pair(viewBox[2] - viewBox[0], viewBox[3] - viewBox[1])
        }
        else -> {
            System.err.println("Unknown image type $image")
            exitProcess(-1)
        }
    }

    val surface = Surface.makeRasterN32Premul((width * scale).roundToInt(), (height * scale).roundToInt())
    surface.canvas.clear(0xFFFFFFFF.toInt())

    val visitor = DrawingVisitor(surface.canvas, scale, scale)
    image.accept(visitor)

    val raster = surface.makeImageSnapshot()

    val pngData =
        raster.encodeToData(EncodedImageFormat.PNG) ?: run {
            System.err.println("Unable to encode image data.")
            exitProcess(-1)
        }

    val byteArray = pngData.bytes

    val data = Base64.encode(byteArray)

    val command = "\u001B_Ga=T,f=100;$data\u001B\\"
    println(command)
}
