package com.jzbrooks.vat

import com.jzbrooks.vgo.core.graphic.ClipPath
import com.jzbrooks.vgo.core.graphic.ContainerElement
import com.jzbrooks.vgo.core.graphic.Element
import com.jzbrooks.vgo.core.graphic.Extra
import com.jzbrooks.vgo.core.graphic.Group
import com.jzbrooks.vgo.core.graphic.Path
import com.jzbrooks.vgo.core.graphic.command.ClosePath
import com.jzbrooks.vgo.core.graphic.command.CubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.CubicCurve
import com.jzbrooks.vgo.core.graphic.command.EllipticalArcCurve
import com.jzbrooks.vgo.core.graphic.command.HorizontalLineTo
import com.jzbrooks.vgo.core.graphic.command.LineTo
import com.jzbrooks.vgo.core.graphic.command.MoveTo
import com.jzbrooks.vgo.core.graphic.command.QuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothCubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothQuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.VerticalLineTo
import com.jzbrooks.vgo.core.transformation.BreakoutImplicitCommands
import com.jzbrooks.vgo.core.transformation.CommandVariant
import com.jzbrooks.vgo.core.util.math.Matrix3
import com.jzbrooks.vgo.core.util.math.Point
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Path as SkiaPath

class DrawingVisitor(val canvas: Canvas) {
    private val pathPreprocessing = listOf(
        BreakoutImplicitCommands(),
        CommandVariant(CommandVariant.Mode.Relative),
    )

    fun render(element: Element) {
        when (element) {
            is ContainerElement -> when (element) {
                is Group -> {
                    val hasTransform = !element.transform.contentsEqual(Matrix3.IDENTITY)
                    if (hasTransform) {
                        canvas.save()
                        canvas.concat(element.transform.toSkiaMatrix33())
                    }
                    for (child in element.elements) render(child)
                    if (hasTransform) {
                        canvas.restore()
                    }
                }
                is ClipPath -> {
                    for (path in element.elements.filterIsInstance<Path>()) {
                        canvas.clipPath(path.toSkiaPath())
                    }
                }
                is Extra -> drawExtra(element)
                else -> for (child in element.elements) render(child)
            }

            is Path -> drawPath(element)
        }
    }

    private fun drawExtra(extra: Extra) {
        when (extra.name) {
            "circle" -> {
                val cx = extra.foreign["cx"]?.toFloatOrNull() ?: return
                val cy = extra.foreign["cy"]?.toFloatOrNull() ?: return
                val r = extra.foreign["r"]?.toFloatOrNull() ?: return
                val fill = extra.foreign["fill"]?.let { parseColor(it) }

                if (fill != null) {
                    canvas.drawCircle(
                        cx,
                        cy,
                        r,
                        Paint().apply {
                            mode = PaintMode.FILL
                            isAntiAlias = true
                            color4f = fill
                        },
                    )
                }
            }
        }
    }

    private fun parseColor(color: String): Color4f? {
        val hex = color.removePrefix("#")
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16)
                val g = hex[1].digitToInt(16)
                val b = hex[2].digitToInt(16)
                Color4f((r * 17) / 255f, (g * 17) / 255f, (b * 17) / 255f, 1f)
            }
            6 -> Color4f(
                hex.substring(0, 2).toInt(16) / 255f,
                hex.substring(2, 4).toInt(16) / 255f,
                hex.substring(4, 6).toInt(16) / 255f,
                1f,
            )
            8 -> Color4f(
                hex.substring(0, 2).toInt(16) / 255f,
                hex.substring(2, 4).toInt(16) / 255f,
                hex.substring(4, 6).toInt(16) / 255f,
                hex.substring(6, 8).toInt(16) / 255f,
            )
            else -> null
        }
    }

    private fun drawPath(path: Path) {
        for (processor in pathPreprocessing) {
            processor.visit(path)
        }

        val strokePaint =
            Paint().apply {
                mode = PaintMode.STROKE
                isAntiAlias = true
                strokeWidth = path.strokeWidth
                strokeMiter = path.strokeMiterLimit
                strokeJoin = when (path.strokeLineJoin) {
                    Path.LineJoin.MITER -> PaintStrokeJoin.MITER
                    Path.LineJoin.ROUND -> PaintStrokeJoin.ROUND
                    Path.LineJoin.BEVEL -> PaintStrokeJoin.BEVEL

                    // todo: these two have no analogs in skia paint
                    Path.LineJoin.ARCS -> PaintStrokeJoin.ROUND
                    Path.LineJoin.MITER_CLIP -> PaintStrokeJoin.MITER
                }
                strokeCap = when (path.strokeLineCap) {
                    Path.LineCap.BUTT -> PaintStrokeCap.BUTT
                    Path.LineCap.ROUND -> PaintStrokeCap.ROUND
                    Path.LineCap.SQUARE -> PaintStrokeCap.SQUARE
                }
                color4f =
                    Color4f(
                        path.stroke.red.toInt() / 255f,
                        path.stroke.green.toInt() / 255f,
                        path.stroke.blue.toInt() / 255f,
                        path.stroke.alpha.toInt() / 255f,
                    )
            }

        val fillPaint =
            Paint().apply {
                mode = PaintMode.FILL
                isAntiAlias = true
                color4f =
                    Color4f(
                        path.fill.red.toInt() / 255f,
                        path.fill.green.toInt() / 255f,
                        path.fill.blue.toInt() / 255f,
                        path.fill.alpha.toInt() / 255f,
                    )
            }

        val skiaPath = path.toSkiaPath()

        canvas.drawPath(skiaPath, strokePaint)
        canvas.drawPath(skiaPath, fillPaint)
    }

    private fun Path.toSkiaPath(): SkiaPath {
        var previousCubicControl = Point.ZERO
        var previousQuadControl = Point.ZERO
        var currentPoint = Point.ZERO
        var subpathStart = Point.ZERO
        val path = PathBuilder(
            when (fillRule) {
                Path.FillRule.NON_ZERO -> PathFillMode.WINDING
                Path.FillRule.EVEN_ODD -> PathFillMode.EVEN_ODD
            },
        ).apply {
            for (command in commands) {
                when (command) {
                    is MoveTo -> {
                        val coord = command.parameters.first()
                        rMoveTo(coord.x, coord.y)
                        currentPoint += coord
                        subpathStart = currentPoint
                    }
                    is LineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(coord.x, coord.y)
                        currentPoint += coord
                    }
                    is HorizontalLineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(coord, 0f)
                        currentPoint += Point(coord, 0f)
                    }
                    is VerticalLineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(0f, coord)
                        currentPoint += Point(0f, coord)
                    }
                    is CubicBezierCurve -> {
                        val params = command.parameters.first()
                        rCubicTo(
                            params.startControl.x,
                            params.startControl.y,
                            params.endControl.x,
                            params.endControl.y,
                            params.end.x,
                            params.end.y,
                        )
                        previousCubicControl = params.endControl + currentPoint
                        currentPoint += params.end
                    }
                    is SmoothCubicBezierCurve -> {
                        val params = command.parameters.first()
                        val reflected = currentPoint - previousCubicControl
                        rCubicTo(reflected.x, reflected.y, params.endControl.x, params.endControl.y, params.end.x, params.end.y)
                        previousCubicControl = params.endControl + currentPoint
                        currentPoint += params.end
                    }
                    is QuadraticBezierCurve -> {
                        val params = command.parameters.first()
                        rQuadTo(params.control.x, params.control.y, params.end.x, params.end.y)
                        previousQuadControl = params.control + currentPoint
                        currentPoint += params.end
                    }
                    is SmoothQuadraticBezierCurve -> {
                        val params = command.parameters.first()
                        val reflected = currentPoint - previousQuadControl
                        rQuadTo(reflected.x, reflected.y, params.x, params.y)
                        previousQuadControl = reflected + currentPoint
                        currentPoint += Point(params.x, params.y)
                    }

                    is EllipticalArcCurve -> {
                        val params = command.parameters.first()
                        rEllipticalArcTo(
                            params.radiusX,
                            params.radiusY,
                            params.angle,
                            when (params.arc) {
                                EllipticalArcCurve.ArcFlag.SMALL -> PathEllipseArc.SMALLER
                                EllipticalArcCurve.ArcFlag.LARGE -> PathEllipseArc.LARGER
                            },
                            when (params.sweep) {
                                EllipticalArcCurve.SweepFlag.ANTICLOCKWISE -> PathDirection.COUNTER_CLOCKWISE
                                EllipticalArcCurve.SweepFlag.CLOCKWISE -> PathDirection.CLOCKWISE
                            },
                            params.end.x,
                            params.end.y,
                        )
                        currentPoint += params.end
                    }

                    ClosePath -> {
                        closePath()
                        currentPoint = subpathStart
                    }
                }

                if (command !is CubicCurve<*>) {
                    previousCubicControl = currentPoint
                }

                if (command !is QuadraticBezierCurve && command !is SmoothQuadraticBezierCurve) {
                    previousQuadControl = currentPoint
                }
            }
        }

        return path.snapshot()
    }

    private fun Matrix3.toSkiaMatrix33(): Matrix33 = Matrix33(
        this[0, 0], this[0, 1], this[0, 2],
        this[1, 0], this[1, 1], this[1, 2],
        this[2, 0], this[2, 1], this[2, 2],
    )
}
