package com.kylecorry.trail_sense.tools.beacons.domain

data class BeaconGroup(
    override val id: Long,
    override val name: String,
    override val parentId: Long? = null,
    override val count: Int = 0
) : IBeacon {
    override val isGroup = true
}