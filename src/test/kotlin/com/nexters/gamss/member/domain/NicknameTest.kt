package com.nexters.gamss.member.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NicknameTest {
    @Test
    fun `유효한 값으로 닉네임을 생성한다`() {
        assertEquals("바다", Nickname("바다").value)
    }

    @Test
    fun `앞뒤 공백을 제거한다`() {
        assertEquals("바다", Nickname("  바다  ").value)
    }

    @Test
    fun `최대 길이까지 허용한다`() {
        val value = "가".repeat(Nickname.MAX_LENGTH)

        assertEquals(value, Nickname(value).value)
    }

    @Test
    fun `최소 길이 미만이면 INVALID_NICKNAME`() {
        val exception = assertFailsWith<BusinessException> { Nickname("가") }

        assertEquals(ErrorCode.INVALID_NICKNAME, exception.errorCode)
    }

    @Test
    fun `공백만 있으면 INVALID_NICKNAME`() {
        val exception = assertFailsWith<BusinessException> { Nickname("   ") }

        assertEquals(ErrorCode.INVALID_NICKNAME, exception.errorCode)
    }

    @Test
    fun `최대 길이를 넘으면 INVALID_NICKNAME`() {
        val exception = assertFailsWith<BusinessException> { Nickname("가".repeat(Nickname.MAX_LENGTH + 1)) }

        assertEquals(ErrorCode.INVALID_NICKNAME, exception.errorCode)
    }

    @Test
    fun `금칙어가 포함되면 INVALID_NICKNAME`() {
        val exception = assertFailsWith<BusinessException> { Nickname("시발이") }

        assertEquals(ErrorCode.INVALID_NICKNAME, exception.errorCode)
    }

    @Test
    fun `값이 같으면 동등하다`() {
        assertEquals(Nickname("바다"), Nickname("바다"))
    }

    @Test
    fun `tryCreate는 유효한 값이면 닉네임을 만든다`() {
        assertEquals(Nickname("바다"), Nickname.tryCreate("바다"))
    }

    @Test
    fun `tryCreate는 규칙에 어긋나면 null을 돌려준다`() {
        assertNull(Nickname.tryCreate("가"))
        assertNull(Nickname.tryCreate("시발이"))
    }

    @Test
    fun `tryCreate는 null이면 null을 돌려준다`() {
        assertNull(Nickname.tryCreate(null))
    }
}
