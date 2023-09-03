package com.kylecorry.trail_sense.navigation.paths.ui.commands

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.coroutines.BackgroundMinimumState
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.gpx.GPXData
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.paths.domain.FullPath
import com.kylecorry.trail_sense.navigation.paths.domain.IPathService
import com.kylecorry.trail_sense.navigation.paths.domain.Path
import com.kylecorry.trail_sense.navigation.paths.domain.PathGPXConverter
import com.kylecorry.trail_sense.navigation.paths.infrastructure.persistence.PathService
import com.kylecorry.trail_sense.shared.io.IOService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant


class ExportPathCommand(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val gpxService: IOService<GPXData>,
    private val pathService: IPathService = PathService.getInstance(context)
) : IPathCommand {

    override fun execute(path: Path) {
        lifecycleOwner.inBackground(BackgroundMinimumState.Created) {
            val waypoints = pathService.getWaypoints(path.id)
            val parent = pathService.getGroup(path.parentId)
            val full = FullPath(path, waypoints, parent)
            val gpx = PathGPXConverter().toGPX(full)
            val exportFile = "trail-sense-${Instant.now().epochSecond}.gpx"
            val success = gpxService.export(gpx, exportFile)
            withContext(Dispatchers.Main) {
                if (success) {
                    Alerts.toast(
                        context,
                        context.getString(R.string.path_exported)
                    )
                } else {
                    Alerts.toast(
                        context,
                        context.getString(R.string.export_path_error)
                    )
                }
            }
        }
    }
}