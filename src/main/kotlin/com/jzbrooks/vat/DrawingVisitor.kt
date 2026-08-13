package com.jzbrooks.vat

import com.jzbrooks.vgo.core.Brush
import com.jzbrooks.vgo.core.Color
import com.jzbrooks.vgo.core.Gradient
import com.jzbrooks.vgo.core.LinearGradient
import com.jzbrooks.vgo.core.RadialGradient
import com.jzbrooks.vgo.core.SweepGradient
import com.jzbrooks.vgo.core.TileMode
import com.jzbrooks.vgo.core.graphic.ContainerElement
import com.jzbrooks.vgo.core.graphic.Element
import com.jzbrooks.vgo.core.graphic.Graphic
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
import com.jzbrooks.vgo.core.transformation.ConvertShapesToPaths
import com.jzbrooks.vgo.core.util.element.traverseTopDown
import com.jzbrooks.vgo.core.util.math.Matrix3
import com.jzbrooks.vgo.core.util.math.Point
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Gradient as SkiaGradient
import org.jetbrains.skia.Path as SkiaPath

class DrawingVisitor(val canvas: Canvas) {
    private val pathPreprocessing = listOf(
        BreakoutImplicitCommands(),
        CommandVariant(CommandVariant.Mode.Relative),
    )

    fun render(element: Element) {
        when (element) {
            is ContainerElement -> {
                when (element) {
                    is Group -> {
                        val hasTransform = !element.transform.contentsEqual(Matrix3.IDENTITY)
                        val hasClip = element.clipPaths.isNotEmpty()
                        if (hasTransform || hasClip) {
                            canvas.save()
                        }
                        if (hasTransform) {
                            canvas.concat(element.transform.toSkiaMatrix33())
                        }
                        for (clipPath in element.clipPaths) {
                            for (region in clipPath.regions) {
                                for (processor in pathPreprocessing) {
                                    processor.visit(region)
                                }
                                canvas.clipPath(region.toSkiaPath())
                            }
                        }
                        for (child in element.elements) render(child)
                        if (hasTransform || hasClip) {
                            canvas.restore()
                        }
                    }
                    is Graphic -> {
                        // Shape conversion is ancestor-scoped, so it has to run across the whole
                        // graphic in one traversal rather than per container.
                        traverseTopDown(element, listOf(ConvertShapesToPaths()))
                        for (child in element.elements) render(child)
                    }
                }
            }

            is Path -> drawPath(element)
        }
    }

    private fun drawPath(path: Path) {
        for (processor in pathPreprocessing) {
            processor.visit(path)
        }

        val skiaPath = path.toSkiaPath()

        val strokePaint = Paint().apply {
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

            path.stroke.applyTo(this)
        }
        canvas.drawPath(skiaPath, strokePaint)

        val fillPaint = Paint().apply {
            mode = PaintMode.FILL
            isAntiAlias = true
            path.fill.applyTo(this)
        }
        canvas.drawPath(skiaPath, fillPaint)
    }

    private fun Brush.applyTo(paint: Paint) {
        when (this) {
            is Color -> paint.color4f = toColor4f()
            is Gradient -> paint.shader = toSkiaShader()
        }
    }

    private fun Gradient.toSkiaShader(): Shader {
        val colors = stops.map { it.color.toColor4f() }.toTypedArray()
        val positions = stops.map { it.offset }.toFloatArray()
        return when (this) {
            is LinearGradient -> Shader.makeLinearGradient(
                startX,
                startY,
                endX,
                endY,
                SkiaGradient(SkiaGradient.Colors(colors, positions, tileMode.toSkiaTileMode())),
            )
            is RadialGradient -> Shader.makeRadialGradient(
                centerX,
                centerY,
                radius,
                SkiaGradient(SkiaGradient.Colors(colors, positions, tileMode.toSkiaTileMode())),
            )
            is SweepGradient -> Shader.makeSweepGradient(
                centerX,
                centerY,
                SkiaGradient(SkiaGradient.Colors(colors, positions, FilterTileMode.CLAMP)),
            )
        }
    }

    private fun TileMode.toSkiaTileMode(): FilterTileMode = when (this) {
        TileMode.CLAMP -> FilterTileMode.CLAMP
        TileMode.REPEAT -> FilterTileMode.REPEAT
        TileMode.MIRROR -> FilterTileMode.MIRROR
    }

    private fun Color.toColor4f(): Color4f = Color4f(
        red.toInt() / 255f,
        green.toInt() / 255f,
        blue.toInt() / 255f,
        alpha.toInt() / 255f,
    )

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
