package com.jzbrooks.vat

import com.jzbrooks.vgo.core.transformation.BakeTransformations
import com.jzbrooks.vgo.core.transformation.BreakoutImplicitCommands
import com.jzbrooks.vgo.core.transformation.CommandVariant
import com.jzbrooks.vgo.core.transformation.TransformerSet
import org.jetbrains.skia.Canvas

internal class RenderingTransformationRegistry(
    canvas: Canvas,
    sX: Float?,
    sY: Float?,
) : TransformerSet(
    listOf(BakeTransformations()),
    listOf(
        BreakoutImplicitCommands(),
        CommandVariant(CommandVariant.Mode.Relative),
        DrawingVisitor(canvas, sX, sY), // This must be last!
    ),
)
