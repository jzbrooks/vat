package com.jzbrooks.vat

import com.jzbrooks.vgo.core.util.ExperimentalVgoApi
import com.jzbrooks.vgo.core.util.element.traverseTopDown
import com.jzbrooks.vgo.iv.ImageVector
import com.jzbrooks.vgo.svg.ScalableVectorGraphic
import com.jzbrooks.vgo.util.parse
import com.jzbrooks.vgo.vd.VectorDrawable
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt
import kotlin.system.exitProcess

private const val HELP_MESSAGE = """
> vat [options] [file]

vat renders vector artwork (SVG & Android Vector Drawable) to the terminal.

Options:
  --background-color   background color in hexadecimal RGBA format
  -s --scale           scale factor (float or integer)
  -h --help            print this message
  -v --version         print the version number
"""

@OptIn(ExperimentalVgoApi::class)
fun main(args: Array<String>) {
    val argReader = ArgReader(args.toMutableList())
    val printHelp = argReader.readFlag("help|h")
    if (printHelp) {
        println(HELP_MESSAGE)
        exitProcess(0)
    }

    val printVersion = argReader.readFlag("version|v")
    if (printVersion) {
        println(BuildConstants.VERSION)
        exitProcess(0)
    }

    val scale = argReader.readOption("scale|s")?.let {
        val factor = it.toFloatOrNull()
        if (factor != null) {
            factor
        } else {
            System.err.println("$it is not a floating point value")
            1f
        }
    } ?: 1f

    val backgroundColor = argReader.readOption("background-color")?.let {
        val rgba = it.removePrefix("0x").removePrefix("#").chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (rgba.size == 4) {
            // The RGBA color must be re-packed as ARGB for skia, per its color packing rules
            (rgba[3] shl 24) or (rgba[0] shl 16) or (rgba[1] shl 8) or rgba[2]
        } else {
            System.err.println("Unable to parse $it as a hexadecimal RGBA color e.g. 0xFF0000FF (red)")
            null
        }
    } ?: 0

    val path = argReader.readArguments().first()
    val image = parse(File(path))

    if (image == null) {
        System.err.println("Unable to load image $path")
        exitProcess(-1)
    }

    val (viewportWidth, viewportHeight) = when (image) {
        is VectorDrawable -> {
            val width = image.foreign["android:viewportWidth"]?.dropLastWhile(Char::isLetter)?.toFloat()
            val height = image.foreign["android:viewportHeight"]?.dropLastWhile(Char::isLetter)?.toFloat()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                System.err.println("Unable to determine image viewport dimensions: $image")
                exitProcess(-1)
            }
        }
        is ScalableVectorGraphic -> {
            // todo: technically SVGs can omit this attribute and draw at a size implicit by the image
            val viewBox = image.foreign["viewBox"]?.split("[\\s,]+".toRegex())?.mapNotNull {
                it.dropLastWhile(Char::isLetter).toFloatOrNull()
            }
            if (viewBox != null && viewBox.size > 3) {
                // todo: since the start coordinates can non-zero, some translation of the
                //  path coordinate system is probably necessary
                Pair(viewBox[2] - viewBox[0], viewBox[3] - viewBox[1])
            } else {
                System.err.println("Unable to determine image viewport dimensions dimensions: $image")
                exitProcess(-1)
            }
        }
        is ImageVector -> {
            Pair(image.viewportWidth, image.viewportHeight)
        }

        else -> {
            System.err.println("Unknown image type $image")
            exitProcess(-1)
        }
    }

    val (width, height) = when (image) {
        is VectorDrawable -> {
            val width = image.foreign["android:width"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
            val height = image.foreign["android:height"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                System.err.println("Unable to determine image dimensions: $image")
                exitProcess(-1)
            }
        }
        is ScalableVectorGraphic -> {
            val width = image.foreign["width"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
            val height = image.foreign["height"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                System.err.println("Unable to determine image dimensions: $image")
                exitProcess(-1)
            }
        }
        is ImageVector -> {
            Pair(image.defaultWidthDp, image.defaultHeightDp)
        }
        else -> {
            System.err.println("Unknown image type $image")
            exitProcess(-1)
        }
    }

    val finalScaleX = (width / viewportWidth) * scale
    val finalScaleY = (height / viewportHeight) * scale

    val surface = Surface.makeRasterN32Premul(
        (viewportWidth * finalScaleX).roundToInt(),
        (viewportHeight * finalScaleY).roundToInt(),
    )

    surface.canvas.clear(backgroundColor)

    val visitor = DrawingVisitor(surface.canvas, finalScaleX, finalScaleY)
    traverseTopDown(image) { it.accept(visitor) }

    val raster = surface.makeImageSnapshot()

    val pngData =
        raster.encodeToData(EncodedImageFormat.PNG) ?: run {
            System.err.println("Unable to encode image data.")
            exitProcess(-1)
        }

    val byteArray = pngData.bytes

    @OptIn(ExperimentalEncodingApi::class)
    val data = Base64.encode(byteArray)

    val command = "\u001B_Ga=T,f=100;$data\u001B\\"
    println(command)
}
