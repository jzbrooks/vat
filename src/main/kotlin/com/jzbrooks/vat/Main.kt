package com.jzbrooks.vat

import com.jzbrooks.vgo.util.parse
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.system.exitProcess

private const val HELP_MESSAGE = """
> vat [options] [file]

vat renders vector artwork (SVG & Android Vector Drawable) to the terminal.

Options:
  --background-color   background color in hexadecimal RGBA format
  -o --output          write the resulting bitmap to a file
  -s --scale           scale factor (float or integer)
  -h --help            print this message
  -v --version         print the version number
"""

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

    val outputPath = argReader.readOption("output|o")

    val scale = argReader.readOption("scale|s")?.let {
        val factor = it.toFloatOrNull()
        if (factor != null) {
            factor
        } else {
            System.err.println("$it is not a floating point value")
            1f
        }
    } ?: 1f

    val backgroundColor = argReader.readOption("background-color")?.let { backgroundColor ->
        val rgba = backgroundColor.removePrefix("0x").removePrefix("#").chunked(2)
            .mapNotNull { it.toIntOrNull(16) }
        if (rgba.size == 4) {
            // The RGBA color must be re-packed as ARGB for skia, per its color packing rules
            (rgba[3] shl 24) or (rgba[0] shl 16) or (rgba[1] shl 8) or rgba[2]
        } else {
            System.err.println("Unable to parse $backgroundColor as a hexadecimal RGBA color e.g. 0xFF0000FF (red)")
            null
        }
    } ?: 0

    val path = argReader.readArguments().first()
    val image = parse(File(path))

    if (image == null) {
        System.err.println("Unable to load image $path")
        exitProcess(-1)
    }

    val byteArray = try {
        renderToPng(image, scale, backgroundColor)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(-1)
    }

    if (outputPath != null) {
        File(outputPath).writeBytes(byteArray)
    } else {
        @OptIn(ExperimentalEncodingApi::class)
        val data = Base64.encode(byteArray)
        val command = "\u001B_Ga=T,f=100;$data\u001B\\"
        println(command)
    }
}
