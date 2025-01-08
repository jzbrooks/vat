package com.jzbrooks.vat

import com.jzbrooks.vgo.vd.parse
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.system.exitProcess

private const val HELP_MESSAGE = """
> vat [options] [file]

vat renders vector artwork (SVG & Android Vector Drawable) to the terminal.

Options:
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

    val path = argReader.readArguments().first()
    val image =
        File(path).inputStream().use { inputStream ->
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()

            val document = documentBuilderFactory.newDocumentBuilder().parse(inputStream)
            document.documentElement.normalize()

            parse(document.documentElement)
        }

    val surface =
        Surface.makeRasterN32Premul(
            image.foreign["android:width"]!!.filter {
                it.isDigit()
            }.toInt(),
            image.foreign["android:height"]!!.filter { it.isDigit() }.toInt(),
        )
    surface.canvas.clear(0xFFFFFFFF.toInt())

    val visitor = DrawingVisitor(surface.canvas)
    image.accept(visitor)

    val raster = surface.makeImageSnapshot()

    val pngData =
        raster.encodeToData(EncodedImageFormat.PNG) ?: run {
            System.err.println("Unable to encode image data.")
            exitProcess(-1)
        }

    val byteArray = pngData.bytes

    val data = Base64.encode(byteArray)

    val command = "\u001B_Ga=T,f=100,Y=00000000;$data\u001B\\"
    println(command)
}
