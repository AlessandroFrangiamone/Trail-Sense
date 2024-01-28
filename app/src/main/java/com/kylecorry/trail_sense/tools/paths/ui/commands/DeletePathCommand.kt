package com.kylecorry.trail_sense.tools.paths.ui.commands

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.tools.paths.domain.IPathService
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class DeletePathCommand(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val pathService: IPathService = PathService.getInstance(context)
) : IPathCommand {

    override fun execute(path: Path) {
        Alerts.dialog(
            context,
            context.getString(R.string.delete_path),
            context.resources.getQuantityString(
                R.plurals.waypoints_to_be_deleted,
                path.metadata.waypoints,
                path.metadata.waypoints
            )
        ) { cancelled ->
            if (!cancelled) {
                lifecycleOwner.inBackground {
                    withContext(Dispatchers.IO) {
                        pathService.deletePath(path)
                    }
                }
            }
        }
    }
}