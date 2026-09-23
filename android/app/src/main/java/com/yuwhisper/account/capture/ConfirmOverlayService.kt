package com.yuwhisper.account.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
 * Floating confirm dialog via SYSTEM_ALERT_WINDOW — stays over WeChat/Alipay without opening the app UI.
 */
class ConfirmOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private val backDispatcher = OnBackPressedDispatcher()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val id = intent?.getLongExtra(EXTRA_PENDING_ID, -1L) ?: -1L
        if (id < 0 || !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "cannot show overlay id=$id canDraw=${Settings.canDrawOverlays(this)}")
            // Prefer notification-only; do not jump into the app.
            stopSelf()
            return START_NOT_STICKY
        }
        if (composeView != null && pendingId == id) {
            return START_NOT_STICKY
        }
        pendingId = id
        mainHandler.post {
            detachOverlay()
            if (!attachOverlay()) {
                Log.w(TAG, "addView failed; notification remains for pendingId=$id")
                stopSelf()
                return@post
            }
            loadData(id)
        }
        return START_NOT_STICKY
    }

    private fun attachOverlay(): Boolean {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = ComposeView(this).apply {
            installOverlayViewTrees(
                lifecycleOwner = this@ConfirmOverlayService,
                viewModelStoreOwner = this@ConfirmOverlayService,
                savedStateRegistryOwner = this@ConfirmOverlayService,
                backDispatcherOwner = this@ConfirmOverlayService,
            )
            setContent {
                AccountTheme {
                    ConfirmPaymentScreen(
                        pending = pending,
                        categories = categories,
                        loadError = loadError,
                        animateIn = false,
                        onConfirm = { amountCents, merchant, categoryLocalId, note, onDone, onError ->
                            val app = application as AccountApp
                            serviceScope.launch {
                                runCatching {
                                    app.ledgerRepository.confirmPendingPayment(
                                        pendingLocalId = pendingId,
                                        amountCents = amountCents,
                                        merchant = merchant,
                                        categoryLocalId = categoryLocalId,
                                        note = note,
                                    )
                                    PendingPaymentNotifier.cancel(this@ConfirmOverlayService, pendingId)
                                }.onSuccess {
                                    onDone()
                                    mainHandler.post { dismissOverlay() }
                                }.onFailure { e ->
                                    Log.e(TAG, "confirm failed", e)
                                    onError(e.message ?: "确认失败")
                                }
                            }
                        },
                        onDismissKeepPending = { dismissOverlay() },
                    )
                }
            }
        }
        composeView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.45f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "惜夏记确认"
        }
        return runCatching {
            wm.addView(view, params)
            view.requestFocus()
            true
        }.getOrElse {
            Log.e(TAG, "add overlay failed", it)
            composeView = null
            false
        }
    }

    private fun loadData(id: Long) {
        val app = application as AccountApp
        loadJob?.cancel()
        loadJob = serviceScope.launch {
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

    private fun dismissOverlay() {
        detachOverlay()
        stopSelf()
    }

    private fun detachOverlay() {
        loadJob?.cancel()
        loadJob = null
        val view = composeView
        composeView = null
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
        }
    }

    override fun onDestroy() {
        detachOverlay()
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
        private const val TAG = "ConfirmOverlay"

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun show(context: Context, pendingId: Long): Boolean {
            if (!canShow(context)) return false
            val appContext = context.applicationContext
            val intent = Intent(appContext, ConfirmOverlayService::class.java)
                .putExtra(EXTRA_PENDING_ID, pendingId)
            return runCatching {
                // Prefer starting from a live service context when provided.
                if (context !== appContext) {
                    context.startService(intent)
                } else {
                    appContext.startService(intent)
                }
                true
            }.getOrElse {
                Log.w(TAG, "start overlay service failed", it)
                false
            }
        }
    }
}
