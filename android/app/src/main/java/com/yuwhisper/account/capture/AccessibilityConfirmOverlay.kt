package com.yuwhisper.account.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.ui.confirm.ConfirmPaymentScreen
import com.yuwhisper.account.ui.theme.AccountTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating confirm card hosted by [PaymentAccessibilityService] using
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] — does not need
 * SYSTEM_ALERT_WINDOW and does not bring the ledger app to the foreground.
 */
class AccessibilityConfirmOverlay(
    private val service: AccessibilityService,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val backDispatcher = OnBackPressedDispatcher()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var loadJob: Job? = null
    private var pending by mutableStateOf<PendingPaymentEntity?>(null)
    private var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    private var loadError by mutableStateOf<String?>(null)
    private var pendingId: Long = -1L

    fun isShowing(): Boolean = composeView?.isAttachedToWindow == true

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Returns true only after the overlay view is actually attached (when called on main). */
    fun show(pendingId: Long): Boolean {
        if (pendingId < 0) return false
        this.pendingId = pendingId
        return runCatching {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showNow(pendingId)
            } else {
                // Caller should prefer main; async path cannot report attach failure.
                mainHandler.post { showNow(pendingId) }
                true
            }
        }.getOrElse {
            Log.w(TAG, "a11y overlay show failed", it)
            false
        }
    }

    private fun showNow(pendingId: Long): Boolean {
        detach()
        if (!attach()) {
            Log.w(TAG, "a11y overlay attach failed pendingId=$pendingId")
            return false
        }
        loadData(pendingId)
        return true
    }

    fun destroy() {
        mainHandler.post {
            detach()
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
            scope.cancel()
        }
    }

    private fun attach(): Boolean {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = ComposeView(service).apply {
            installOverlayViewTrees(
                lifecycleOwner = this@AccessibilityConfirmOverlay,
                viewModelStoreOwner = this@AccessibilityConfirmOverlay,
                savedStateRegistryOwner = this@AccessibilityConfirmOverlay,
                backDispatcherOwner = this@AccessibilityConfirmOverlay,
            )
            setContent {
                AccountTheme {
                    ConfirmPaymentScreen(
                        pending = pending,
                        categories = categories,
                        loadError = loadError,
                        animateIn = false,
                        onConfirm = { amountCents, merchant, categoryLocalId, note, onDone, onError ->
                            val app = service.application as AccountApp
                            scope.launch {
                                runCatching {
                                    app.ledgerRepository.confirmPendingPayment(
                                        pendingLocalId = pendingId,
                                        amountCents = amountCents,
                                        merchant = merchant,
                                        categoryLocalId = categoryLocalId,
                                        note = note,
                                    )
                                    PendingPaymentNotifier.cancel(service, pendingId)
                                }.onSuccess {
                                    onDone()
                                    mainHandler.post { detach() }
                                }.onFailure { e ->
                                    Log.e(TAG, "confirm failed", e)
                                    onError(e.message ?: "确认失败")
                                }
                            }
                        },
                        onDismissKeepPending = { detach() },
                    )
                }
            }
        }
        composeView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.45f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "惜夏记确认"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return runCatching {
            wm.addView(view, params)
            view.requestFocus()
            true
        }.getOrElse {
            Log.e(TAG, "add a11y overlay failed", it)
            composeView = null
            false
        }
    }

    private fun loadData(id: Long) {
        val app = service.application as AccountApp
        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching {
                app.ensureSeeded()
                val row = app.ledgerRepository.getPendingPayment(id)
                if (row == null) {
                    loadError = "待确认记录不存在或已入账"
                    return@runCatching
                }
                pending = row
                app.ledgerRepository.observeCategories().collect { list ->
                    categories = list
                }
            }.onFailure { e ->
                Log.e(TAG, "load pending failed", e)
                loadError = e.message ?: "加载失败"
            }
        }
    }

    private fun detach() {
        loadJob?.cancel()
        loadJob = null
        val view = composeView
        composeView = null
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
        }
        pending = null
        loadError = null
    }

    companion object {
        private const val TAG = "A11yConfirmOverlay"
    }
}
