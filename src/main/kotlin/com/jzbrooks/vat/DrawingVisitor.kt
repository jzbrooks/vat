package com.jzbrooks.vat

import com.jzbrooks.vgo.core.graphic.ClipPath
import com.jzbrooks.vgo.core.graphic.ElementVisitor
import com.jzbrooks.vgo.core.graphic.Extra
import com.jzbrooks.vgo.core.graphic.Graphic
import com.jzbrooks.vgo.core.graphic.Group
import com.jzbrooks.vgo.core.graphic.Path
import com.jzbrooks.vgo.core.graphic.command.ClosePath
import com.jzbrooks.vgo.core.graphic.command.CubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.EllipticalArcCurve
import com.jzbrooks.vgo.core.graphic.command.HorizontalLineTo
import com.jzbrooks.vgo.core.graphic.command.LineTo
import com.jzbrooks.vgo.core.graphic.command.MoveTo
import com.jzbrooks.vgo.core.graphic.command.QuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothCubicBezierCurve
import com.jzbrooks.vgo.core.graphic.command.SmoothQuadraticBezierCurve
import com.jzbrooks.vgo.core.graphic.command.VerticalLineTo
import com.jzbrooks.vgo.core.optimization.BreakoutImplicitCommands
import com.jzbrooks.vgo.core.optimization.CommandVariant
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathEllipseArc
import org.jetbrains.skia.Path as SkiaPath

class DrawingVisitor(val canvas: Canvas) : ElementVisitor {
    override fun visit(graphic: Graphic) {
        for (element in graphic.elements) {
            element.accept(this)
        }
    }

    override fun visit(clipPath: ClipPath) {
        for (element in clipPath.elements.filterIsInstance<Path>()) {
            canvas.clipPath(element.toSkiaPath())
        }
    }

    override fun visit(group: Group) {
        for (element in group.elements) {
            element.accept(this)
        }
    }

    override fun visit(extra: Extra) {
        for (element in extra.elements) {
            element.accept(this)
        }
    }

    override fun visit(path: Path) {
        val strokePaint =
            Paint().apply {
                isAntiAlias = true
                setStroke(true)
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

        var previousControlPoint: com.jzbrooks.vgo.core.util.math.Point? = null
        return SkiaPath().apply {
            for (command in commands) {
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
                        rLineTo(coord, 0f)
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
    }
}
