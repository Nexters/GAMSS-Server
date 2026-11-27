package com.nexters.gamss.game.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameRecordTest {
    @Test
    fun `0점부터 상한까지 저장할 수 있다`() {
        assertEquals(0, GameRecord(1L, GameType.CLICKER, 0).score)
        assertEquals(99_999, GameRecord(1L, GameType.CLICKER, 99_999).score)
    }

    @Test
    fun `음수 점수는 거부한다`() {
        val e = assertFailsWith<BusinessException> { GameRecord(1L, GameType.CLICKER, -1) }

        assertEquals(ErrorCode.INVALID_GAME_SCORE, e.errorCode)
    }

    @Test
    fun `상한을 넘는 점수는 거부한다`() {
        val e = assertFailsWith<BusinessException> { GameRecord(1L, GameType.CLICKER, 100_000) }

        assertEquals(ErrorCode.INVALID_GAME_SCORE, e.errorCode)
    }
}
