package com.kylecorry.trail_sense.tools.augmented_reality.position

import com.kylecorry.trail_sense.tools.augmented_reality.AugmentedRealityView

/**
 * A point in the AR world
 */
interface ARPoint {
    /**
     * Gets the horizon (spherical) coordinate of the point
     */
    fun getHorizonCoordinate(view: AugmentedRealityView): AugmentedRealityView.HorizonCoordinate

    fun getAugmentedRealityCoordinate(view: AugmentedRealityView): AugmentedRealityCoordinate

    /**
     * Gets the angular diameter of the point in degrees
     */
    fun getAngularDiameter(view: AugmentedRealityView): Float
}