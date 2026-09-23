package com.yuwhisper.account.capture

import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * ComposeView in a WindowManager overlay is not hosted by ComponentActivity,
 * so ViewTree owners (including back dispatcher) must be installed manually.
 * Missing [OnBackPressedDispatcherOwner] makes [androidx.activity.compose.BackHandler]
 * crash the composition — the overlay window stays attached but the card never draws.
 */
internal fun View.installOverlayViewTrees(
    lifecycleOwner: LifecycleOwner,
    viewModelStoreOwner: ViewModelStoreOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    backDispatcherOwner: OnBackPressedDispatcherOwner,
) {
    setViewTreeLifecycleOwner(lifecycleOwner)
    setViewTreeViewModelStoreOwner(viewModelStoreOwner)
    setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
    setViewTreeOnBackPressedDispatcherOwner(backDispatcherOwner)
    isFocusable = true
    isFocusableInTouchMode = true
    setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            val dispatcher = backDispatcherOwner.onBackPressedDispatcher
            if (dispatcher.hasEnabledCallbacks()) {
                dispatcher.onBackPressed()
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}
