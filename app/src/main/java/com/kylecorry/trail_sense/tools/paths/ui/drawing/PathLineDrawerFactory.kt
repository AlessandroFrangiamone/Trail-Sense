package com.kylecorry.trail_sense.tools.paths.ui.drawing

import com.kylecorry.trail_sense.tools.paths.domain.LineStyle

class PathLineDrawerFactory {

    fun create(style: LineStyle): IPathLineDrawerStrategy {
        return when (style) {
            LineStyle.Solid -> SolidPathLineDrawerStrategy()
            LineStyle.Dotted -> DottedPathLineDrawerStrategy()
            LineStyle.Arrow -> ArrowPathLineDrawerStrategy()
            LineStyle.Dashed -> DashedPathLineDrawerStrategy()
            LineStyle.Square -> SquarePathLineDrawerStrategy()
            LineStyle.Diamond -> DiamondPathLineDrawerStrategy()
            LineStyle.Cross -> CrossPathLineDrawerStrategy()
        }
    }

}