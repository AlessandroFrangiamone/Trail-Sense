package com.kylecorry.trail_sense.tools.paths.ui.commands

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.tools.paths.domain.IPathService
import com.kylecorry.trail_sense.tools.paths.domain.Path
import com.kylecorry.trail_sense.tools.paths.infrastructure.persistence.PathService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class RenamePathCommand(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val pathService: IPathService = PathService.getInstance(context)
) : IPathCommand {

    override fun execute(path: Path) {
        Pickers.text(
            context,
            context.getString(R.string.rename),
            default = path.name,
            hint = context.getString(R.string.name)
        ) {
            if (it != null) {
                lifecycleOwner.inBackground {
                    withContext(Dispatchers.IO) {
                        pathService.addPath(path.copy(name = it.ifBlank { null }))
                    }
                }

            }
        }
    }
}