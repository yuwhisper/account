package com.yuwhisper.account.domain

/** Preset seed category names (order = sortOrder). */
object SeedCategories {
    val NAMES: List<String> = listOf(
        "餐饮",
        "交通",
        "购物",
        "住房",
        "娱乐",
        "医疗",
        "教育",
        "其他",
    )
}

/**
 * Default recognition-scene (watch) apps.
 * enabled=true means monitored by default.
 */
data class WatchAppSeed(
    val packageName: String,
    val label: String,
    val enabled: Boolean = true,
)

object DefaultWatchApps {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val UNIONPAY = "com.unionpay"

    val ALL: List<WatchAppSeed> = listOf(
        WatchAppSeed(WECHAT, "微信", enabled = true),
        WatchAppSeed(ALIPAY, "支付宝", enabled = true),
        WatchAppSeed(UNIONPAY, "云闪付", enabled = true),
        WatchAppSeed("com.chinamworld.main", "中国银行", enabled = false),
        WatchAppSeed("com.icbc", "工商银行", enabled = false),
        WatchAppSeed("com.android.bankabc", "农业银行", enabled = false),
        WatchAppSeed("com.chinamworld.bocmbci", "中国银行手机银行", enabled = false),
        WatchAppSeed("cmb.pb", "招商银行", enabled = false),
        WatchAppSeed("com.ecitic.bank.mobile", "中信银行", enabled = false),
    )
}

object SyncMetaKeys {
    const val TRANSACTION_CURSOR = "transaction_cursor"
    const val CATEGORY_CURSOR = "category_cursor"
    const val ACCESS_TOKEN = "access_token"
    const val USER_EMAIL = "user_email"
}
