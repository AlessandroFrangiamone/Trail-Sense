package com.kylecorry.trail_sense.tools.beacons.infrastructure.sort.mappers

import com.kylecorry.trail_sense.tools.beacons.domain.IBeacon
import com.kylecorry.trail_sense.shared.grouping.mapping.ISuspendMapper

class BeaconNameMapper : ISuspendMapper<IBeacon, String> {
    override suspend fun map(item: IBeacon): String {
        return item.name
    }
}