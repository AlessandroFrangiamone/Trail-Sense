package com.kylecorry.trail_sense.tools.paths.ui.commands

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.tools.paths.domain.IPathService
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.domain.PathPointColoringStyle
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class ChangePointStyleCommand(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val pathService: IPathService = PathService.getInstance(context)
) : IPathCommand {

    override fun execute(path: Path) {
        Pickers.item(
            context, context.getString(R.string.point_style), listOf(
                context.getString(R.string.none),
                context.getString(R.string.cell_signal),
                context.getString(R.string.elevation),
                context.getString(R.string.time),
                context.getString(R.string.path_slope)
            ),
            defaultSelectedIndex = path.style.point.ordinal
        ) {
            if (it != null) {
                val pointStyle =
                    PathPointColoringStyle.values().find { style -> style.ordinal == it }
                        ?: PathPointColoringStyle.None
                lifecycleOwner.inBackground {
                    withContext(Dispatchers.IO) {
                        pathService.addPath(path.copy(style = path.style.copy(point = pointStyle)))
                    }
                }
            }
        }
    }
}