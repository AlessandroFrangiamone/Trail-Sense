package com.kylecorry.trail_sense.shared.canvas

import com.kylecorry.andromeda.core.units.PixelCoordinate

data class PixelCircle(val center: PixelCoordinate, val radius: Float){
    fun contains(pixel: PixelCoordinate): Boolean {
        val distance = center.distanceTo(pixel)
        return distance <= radius
    }

    fun intersects(other: PixelCircle): Boolean {
        val distance = center.distanceTo(other.center)
        return distance <= radius + other.radius
    }

}
