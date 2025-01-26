package com.jzbrooks.vat

import com.jzbrooks.vgo.core.graphic.ClipPath
import com.jzbrooks.vgo.core.graphic.ElementVisitor
import com.jzbrooks.vgo.core.graphic.Extra
import com.jzbrooks.vgo.core.graphic.Graphic
import com.jzbrooks.vgo.core.graphic.Group
import com.jzbrooks.vgo.core.graphic.Path
import com.jzbrooks.vgo.core.graphic.command.ClosePath
import com.jzbrooks.vgo.core.graphic.command.Command
import com.jzbrooks.vgo.core.graphic.command.CubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.EllipticalArcCurve
import com.jzbrooks.vgo.core.graphic.command.HorizontalLineTo
import com.jzbrooks.vgo.core.graphic.command.LineTo
import com.jzbrooks.vgo.core.graphic.command.MoveTo
import com.jzbrooks.vgo.core.graphic.command.QuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothCubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothQuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.VerticalLineTo
import com.jzbrooks.vgo.core.optimization.BakeTransformations
import com.jzbrooks.vgo.core.optimization.BreakoutImplicitCommands
import com.jzbrooks.vgo.core.optimization.CommandVariant
import com.jzbrooks.vgo.core.util.math.Point
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Path as SkiaPath

class DrawingVisitor(val canvas: Canvas, private val sX: Float?, private val sY: Float?) : ElementVisitor {
    override fun visit(graphic: Graphic) {}

    override fun visit(clipPath: ClipPath) {
        for (path in clipPath.elements.filterIsInstance<Path>()) {
            canvas.clipPath(path.toSkiaPath())
        }
    }

    override fun visit(group: Group) {
        BakeTransformations().visit(group)
    }

    override fun visit(extra: Extra) {}

    override fun visit(path: Path) {
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
        BreakoutImplicitCommands().visit(this)
        CommandVariant(CommandVariant.Mode.Relative).visit(this)

        var previousControlPoint = Point.ZERO
        val path = SkiaPath().apply {
            fillMode = when (fillRule) {
                Path.FillRule.NON_ZERO -> PathFillMode.WINDING
                Path.FillRule.EVEN_ODD -> PathFillMode.EVEN_ODD
            }

            for (command in commands) {
                if (command.shouldResetPreviousControlPoint) {
                    previousControlPoint = Point.ZERO
                }

                when (command) {
                    is MoveTo -> {
                        val coord = command.parameters.first()
                        rMoveTo(coord.x, coord.y)
                    }
                    is LineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(coord.x, coord.y)
                    }
                    is HorizontalLineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(coord, 0f)
                    }
                    is VerticalLineTo -> {
                        val coord = command.parameters.first()
                        rLineTo(0f, coord)
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
                        previousControlPoint = params.endControl
                    }
                    is SmoothCubicBezierCurve -> {
                        val params = command.parameters.first()
                        val reflected = checkNotNull(previousControlPoint) * -1f
                        rCubicTo(reflected.x, reflected.y, params.endControl.x, params.endControl.y, params.end.x, params.end.y)
                        previousControlPoint = params.endControl
                    }
                    is QuadraticBezierCurve -> {
                        val params = command.parameters.first()
                        rQuadTo(params.control.x, params.control.y, params.end.x, params.end.y)
                        previousControlPoint = params.control
                    }
                    is SmoothQuadraticBezierCurve -> {
                        val params = command.parameters.first()
                        val reflected = checkNotNull(previousControlPoint) * -1f
                        rQuadTo(reflected.x, reflected.y, params.x, params.y)
                        previousControlPoint = reflected
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
                    }

                    ClosePath -> closePath()
                }
            }
        }

        return if (sX != null && sY != null) {
            val scale = Matrix33.makeScale(sX, sY)
            path.transform(scale)
        } else {
            path
        }
    }

    private val Command.shouldResetPreviousControlPoint: Boolean
        get() =
            when (this) {
                is MoveTo,
                is LineTo,
                is HorizontalLineTo,
                is VerticalLineTo,
                is EllipticalArcCurve,
                ClosePath,
                -> true

                is CubicBezierCurve,
                is SmoothCubicBezierCurve,
                is QuadraticBezierCurve,
                is SmoothQuadraticBezierCurve,
                -> false
            }
}
