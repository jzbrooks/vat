package com.jzbrooks.vat

import com.jzbrooks.vgo.core.graphic.Graphic
import com.jzbrooks.vgo.core.util.ExperimentalVgoApi
import com.jzbrooks.vgo.iv.ImageVector
import com.jzbrooks.vgo.svg.ScalableVectorGraphic
import com.jzbrooks.vgo.vd.VectorDrawable
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

@OptIn(ExperimentalVgoApi::class)
fun renderToPng(
    image: Graphic,
    scale: Float = 1f,
    backgroundColor: Int = 0,
): ByteArray {
    val (viewportWidth, viewportHeight) = when (image) {
        is VectorDrawable -> {
            val width = image.foreign["android:viewportWidth"]?.dropLastWhile(Char::isLetter)?.toFloat()
            val height = image.foreign["android:viewportHeight"]?.dropLastWhile(Char::isLetter)?.toFloat()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                throw IllegalArgumentException("Unable to determine image viewport dimensions: $image")
            }
        }
        is ScalableVectorGraphic -> {
            val viewBox = image.foreign["viewBox"]?.split("[\\s,]+".toRegex())?.mapNotNull {
                it.dropLastWhile(Char::isLetter).toFloatOrNull()
            }
            if (viewBox != null && viewBox.size > 3) {
                Pair(viewBox[2] - viewBox[0], viewBox[3] - viewBox[1])
            } else {
                val width = image.foreign["width"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
                val height = image.foreign["height"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
                if (width != null && height != null) {
                    Pair(width, height)
                } else {
                    throw IllegalArgumentException("Unable to determine image viewport dimensions")
                }
            }
        }
        is ImageVector -> {
            Pair(image.viewportWidth, image.viewportHeight)
        }
        else -> {
            throw IllegalArgumentException("Unknown image type $image")
        }
    }

    val (width, height) = when (image) {
        is VectorDrawable -> {
            val width = image.foreign["android:width"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
            val height = image.foreign["android:height"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                throw IllegalArgumentException("Unable to determine image dimensions: $image")
            }
        }
        is ScalableVectorGraphic -> {
            val width = image.foreign["width"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()
            val height = image.foreign["height"]?.dropLastWhile(Char::isLetter)?.toFloatOrNull()

            if (width != null && height != null) {
                Pair(width, height)
            } else {
                Pair(viewportWidth, viewportHeight)
            }
        }
        is ImageVector -> {
            Pair(image.defaultWidthDp, image.defaultHeightDp)
        }
        else -> {
            throw IllegalArgumentException("Unknown image type $image")
        }
    }

    val finalScaleX = (width / viewportWidth) * scale
    val finalScaleY = (height / viewportHeight) * scale

    val surface = Surface.makeRasterN32Premul(
        (viewportWidth * finalScaleX).roundToInt(),
        (viewportHeight * finalScaleY).roundToInt(),
    )

    surface.canvas.clear(backgroundColor)
    surface.canvas.scale(finalScaleX, finalScaleY)

    DrawingVisitor(surface.canvas).render(image)

    val raster = surface.makeImageSnapshot()

    val pngData = raster.encodeToData(EncodedImageFormat.PNG)
        ?: throw IllegalStateException("Unable to encode image data.")

    return pngData.bytes
}
