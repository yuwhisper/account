package com.yuwhisper.account.capture

/**
 * In-memory diagnostics for why capture did / did not fire (shown in Settings).
 */
object CaptureDebug {
    @Volatile
    var lastNote: String = "尚无捕获记录（付款后回到设置可查看）"
        private set

    fun note(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date())
        lastNote = "$ts $message".take(800)
        android.util.Log.i(TAG, message)
    }

    private const val TAG = "CaptureDebug"
}
