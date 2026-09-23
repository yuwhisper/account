package com.yuwhisper.account.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParseTest {

    @Test
    fun parseWechatNotification() {
        val raw = ParsedRaw(
            title = "微信支付",
            text = "收款方商家：瑞幸咖啡\n支付金额：¥36.50",
        )
        val r = PaymentParser.parseWechat(raw)
        assertNotNull(r)
        assertEquals(3650, r!!.amountCents)
        assertTrue(r.merchant.contains("瑞幸"))
    }

    @Test
    fun parseAlipayNotification() {
        val raw = ParsedRaw(
            title = "支付宝",
            text = "付款成功\n商户：星巴克咖啡\n金额：￥28.00",
        )
        val r = PaymentParser.parseAlipay(raw)
        assertNotNull(r)
        assertEquals(2800, r!!.amountCents)
        assertTrue(r.merchant.contains("星巴克"))
    }

    @Test
    fun parseRoutesByPackageName() {
        val wechat = PaymentParser.parse(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "收款方商家：瑞幸咖啡\n支付金额：¥36.50",
        )
        assertNotNull(wechat)
        assertEquals(3650, wechat!!.amountCents)
        assertEquals("wechat", wechat.source)

        val alipay = PaymentParser.parse(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "付款成功\n商户：星巴克咖啡\n金额：￥28.00",
        )
        assertNotNull(alipay)
        assertEquals(2800, alipay!!.amountCents)
        assertEquals("alipay", alipay.source)
    }

    @Test
    fun parseUnknownPackageWithAmount() {
        val r = PaymentParser.parse(
            packageName = "com.example.unknown",
            title = "付款",
            text = "金额：¥10.00",
        )
        assertNotNull(r)
        assertEquals(1000, r!!.amountCents)
    }

    @Test
    fun parseAccessibilitySplitYenAndAmount() {
        val r = PaymentParser.parseAccessibility(
            packageName = "com.tencent.mm",
            texts = listOf("支付成功", "¥", "12.34", "完成"),
        )
        assertNotNull(r)
        assertEquals(1234, r!!.amountCents)
    }

    @Test
    fun parseWechatRedPacketAccessibility() {
        val r = PaymentParser.parseAccessibility(
            packageName = "com.tencent.mm",
            texts = listOf("红包", "已发送", "¥", "0.30", "给小明的红包", "看看大家的手气"),
        )
        assertNotNull(r)
        assertEquals(30, r!!.amountCents)
        assertTrue(
            PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "你发了一个红包 ¥0.30 等待对方领取",
                accessibilityMode = true,
            ),
        )
        assertTrue(
            !PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "塞钱进红包 ¥0.30 金额",
                accessibilityMode = true,
            ),
        )
    }

    @Test
    fun transferSuccessOffersConfirm() {
        assertTrue(
            PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "待朋友确认收款 ¥1.00",
                accessibilityMode = true,
            ),
        )
        assertTrue(
            PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "转账成功 ¥2.00 转账给小红",
                accessibilityMode = true,
            ),
        )
    }

    @Test
    fun strongSuccessRequiredForA11yOffer() {
        assertTrue(
            PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "支付成功 ¥3.00",
                accessibilityMode = true,
            ),
        )
        assertTrue(
            !PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "",
                "微信支付 聊天列表",
                accessibilityMode = true,
            ),
        )
    }

    @Test
    fun alipayHomeDoesNotOfferConfirm() {
        val home = listOf(
            "首页", "理财", "消息", "我的",
            "余额", "花呗", "转账", "出行",
            "扣款", "¥12.00", "消费",
        )
        assertTrue(
            !PaymentParser.shouldOfferConfirmFromNodes(
                "com.eg.android.AlipayGphone",
                home,
            ),
        )
        assertTrue(
            PaymentParser.shouldOfferConfirmFromNodes(
                "com.eg.android.AlipayGphone",
                listOf("支付成功", "¥28.00", "星巴克", "完成"),
            ),
        )
    }

    @Test
    fun incomePageDoesNotOfferConfirm() {
        assertTrue(
            !PaymentParser.shouldOfferConfirmFromNodes(
                "com.tencent.mm",
                listOf("已存入零钱", "¥5.00", "红包"),
            ),
        )
        assertTrue(
            !PaymentParser.shouldOfferConfirm(
                "com.tencent.mm",
                "微信支付",
                "收款成功 ¥5.00",
                accessibilityMode = true,
            ),
        )
    }

    @Test
    fun wechatRedPacketSendResultPageOffersConfirm() {
        val result = listOf("已发送", "红包", "¥", "0.3", "未领取的红包将于24小时后发起退款", "完成")
        assertTrue(PaymentParser.shouldOfferConfirmFromNodes("com.tencent.mm", result))
        val parsed = PaymentParser.parseAccessibility("com.tencent.mm", result)
        assertNotNull(parsed)
        assertEquals(30, parsed!!.amountCents)
    }

    @Test
    fun alipayNotificationRequiresSuccessCopy() {
        assertTrue(
            !PaymentParser.shouldOfferConfirm(
                "com.eg.android.AlipayGphone",
                "支付宝",
                "账户扣款 ¥12.00",
                accessibilityMode = false,
            ),
        )
        assertTrue(
            PaymentParser.shouldOfferConfirm(
                "com.eg.android.AlipayGphone",
                "支付宝",
                "付款成功 商户：星巴克 金额：¥28.00",
                accessibilityMode = false,
            ),
        )
    }
}
