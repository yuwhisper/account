package com.yuwhisper.account.capture

/**
 * In-memory diagnostics for why capture did / did not fire (shown in Settings).
 */
object CaptureDebug {
    @Volatile
    var lastNote: String = "尚无捕获记录（付款后回到设置可查看）"
        private set

    @Volatile
    var lastSample: String = ""
        private set

    fun note(message: String, sample: String? = null) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date())
        lastNote = "$ts $message".take(800)
        if (sample != null) {
            lastSample = sample.take(1200)
        }
        android.util.Log.i(TAG, message)
    }

    fun report(): String = buildString {
        append(lastNote)
        if (lastSample.isNotBlank()) {
            append("\n画面文案：")
            append(lastSample)
        }
    }

    private const val TAG = "CaptureDebug"
}
