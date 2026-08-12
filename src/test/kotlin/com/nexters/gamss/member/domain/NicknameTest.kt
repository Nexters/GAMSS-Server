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
    fun `이모지 하나는 1자로 세어 최소 길이 미달이다`() {
        // String.length 기준이던 시절엔 서로게이트 페어(2 유닛) 때문에 통과하던 입력이다.
        val exception = assertFailsWith<BusinessException> { Nickname("😀") }

        assertEquals(ErrorCode.INVALID_NICKNAME, exception.errorCode)
    }

    @Test
    fun `이모지도 사용자가 보는 글자 수로 센다`() {
        assertEquals("😀😀", Nickname("😀😀").value)
        assertEquals("😀".repeat(Nickname.MAX_LENGTH), Nickname("😀".repeat(Nickname.MAX_LENGTH)).value)
        assertFailsWith<BusinessException> { Nickname("😀".repeat(Nickname.MAX_LENGTH + 1)) }
    }

    @Test
    fun `조합 이모지 최대 길이도 허용한다 - 컬럼 방어는 코드 포인트 기준`() {
        // 👨‍👩‍👧‍👦 1자 = 코드 포인트 7개·UTF-16 유닛 11개. 20자면 코드 포인트 140개로 varchar(200)에
        // 들어가야 한다. UTF-16 유닛(220개)으로 방어하면 이 정상 입력이 거부된다.
        val family = "👨‍👩‍👧‍👦".repeat(Nickname.MAX_LENGTH)

        assertEquals(family, Nickname(family).value)
    }

    @Test
    fun `피부톤·ZWJ 조합 이모지는 한 자로 센다`() {
        // 👍🏽 = 엄지 + 피부톤(코드 포인트 2개), 👨‍👩‍👧 = ZWJ 가족(코드 포인트 5개). 각각 그래핌 1자.
        assertFailsWith<BusinessException> { Nickname("👍🏽") }
        assertFailsWith<BusinessException> { Nickname("👨‍👩‍👧") }
        assertEquals("👍🏽바다", Nickname("👍🏽바다").value)
        assertEquals("👨‍👩‍👧바다", Nickname("👨‍👩‍👧바다").value)
    }

    @Test
    fun `그래핌 수가 맞아도 코드 유닛이 컬럼 한계를 넘으면 INVALID_NICKNAME`() {
        // ZWJ로 무한정 이어 붙인 병적인 단일 그래핌. 검증을 통과시키면 varchar(200) 삽입에서 터진다.
        val hugeCluster = List(60) { "👨" }.joinToString("\u200D")

        val exception = assertFailsWith<BusinessException> { Nickname("${hugeCluster}가$hugeCluster") }

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
