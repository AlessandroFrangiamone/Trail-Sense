package com.kylecorry.trail_sense.quickactions

import android.widget.ImageButton
import com.kylecorry.trail_sense.databinding.ActivityNavigatorBinding
import com.kylecorry.trail_sense.navigation.infrastructure.NavigationPreferences
import com.kylecorry.trail_sense.navigation.ui.NavigatorFragment
import com.kylecorry.trail_sense.shared.QuickActionButton
import com.kylecorry.trail_sense.shared.QuickActionType
import com.kylecorry.trail_sense.shared.views.QuickActionNone

class NavigationQuickActionBinder(
    private val fragment: NavigatorFragment,
    private val binding: ActivityNavigatorBinding,
    private val prefs: NavigationPreferences
) : IQuickActionBinder {

    override fun bind() {
        getQuickActionButton(
            prefs.leftButton,
            binding.navigationTitle.leftButton
        ).bind(fragment)

        getQuickActionButton(
            prefs.rightButton,
            binding.navigationTitle.rightButton
        ).bind(fragment)
    }

    private fun getQuickActionButton(
        type: QuickActionType,
        button: ImageButton
    ): QuickActionButton {
        return when (type) {
            QuickActionType.None -> QuickActionNone(button, fragment)
            QuickActionType.Paths -> QuickActionPaths(button, fragment)
            QuickActionType.Flashlight -> QuickActionFlashlight(button, fragment)
            QuickActionType.Ruler -> QuickActionRuler(button, fragment, binding.ruler)
            QuickActionType.Maps -> QuickActionOfflineMaps(button, fragment)
            QuickActionType.Whistle -> QuickActionWhistle(button, fragment)
            QuickActionType.LowPowerMode -> LowPowerQuickAction(button, fragment)
            QuickActionType.NightMode -> QuickActionNightMode(button, fragment)
            QuickActionType.Backtrack -> QuickActionBacktrack(button, fragment)
            QuickActionType.Pedometer -> QuickActionPedometer(button, fragment)
            QuickActionType.ScreenFlashlight -> QuickActionScreenFlashlight(button, fragment)
            else -> QuickActionNone(button, fragment)
        }
    }
}