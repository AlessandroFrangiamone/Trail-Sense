package com.kylecorry.trail_sense.tools.paths.domain.pathsort

import com.kylecorry.trail_sense.tools.paths.domain.IPath

interface IPathSortStrategy {
    suspend fun sort(paths: List<IPath>): List<IPath>
}