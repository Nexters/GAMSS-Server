package com.nexters.gamss.notification.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FcmTokenTest {
    @Test
    fun `앞뒤 공백을 제거한다`() {
        assertEquals("abc", FcmToken("  abc  ").value)
    }

    @Test
    fun `공백뿐이면 거부한다`() {
        val e = assertFailsWith<BusinessException> { FcmToken("   ") }

        assertEquals(ErrorCode.INVALID_DEVICE_TOKEN, e.errorCode)
    }

    @Test
    fun `컬럼 크기까지는 허용하고 넘으면 거부한다`() {
        FcmToken("a".repeat(FcmToken.COLUMN_LENGTH))

        assertFailsWith<BusinessException> { FcmToken("a".repeat(FcmToken.COLUMN_LENGTH + 1)) }
    }

    @Test
    fun `콜론이 섞인 실제 FCM 토큰 형태를 그대로 받는다`() {
        val raw = "fMEk9dQvS0m1:APA91bH-_x9Yq.ZzW~n"

        assertEquals(raw, FcmToken(raw).value)
    }

    @Test
    fun `같은 값이면 동등하다`() {
        assertEquals(FcmToken("abc"), FcmToken(" abc "))
        assertEquals(FcmToken("abc").hashCode(), FcmToken("abc").hashCode())
        assertFalse(FcmToken("abc") == FcmToken("abd"))
    }

    @Test
    fun `로그에 토큰 전체가 드러나지 않는다`() {
        val raw = "a".repeat(200)

        assertFalse(FcmToken(raw).toString().contains(raw))
        assertTrue(FcmToken(raw).toString().contains("..."))
    }
}
