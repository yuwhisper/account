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
    fun parseUnknownPackageReturnsNull() {
        val r = PaymentParser.parse(
            packageName = "com.example.unknown",
            title = "付款",
            text = "金额：¥10.00",
        )
        assertNull(r)
    }
}
