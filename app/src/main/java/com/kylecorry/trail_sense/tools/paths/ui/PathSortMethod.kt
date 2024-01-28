package com.kylecorry.trail_sense.tools.paths.ui

import com.kylecorry.trail_sense.shared.data.Identifiable

enum class PathSortMethod(override val id: Long) : Identifiable {
    MostRecent(1),
    Longest(2),
    Shortest(3),
    Closest(4),
    Name(5)
}