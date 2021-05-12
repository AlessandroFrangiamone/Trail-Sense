package com.kylecorry.trail_sense.settings

import android.content.Context
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.preferences.BooleanPreference

class FlashlightPreferenceRepo(context: Context) : PreferenceRepo(context) {

    var toggleWithSystem by BooleanPreference(
        cache,
        getString(R.string.pref_flashlight_toggle_with_system),
        true
    )

}