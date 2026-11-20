package com.nexters.gamss.game.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameTypeTest {
    @Test
    fun `경로 이름으로 게임을 찾는다`() {
        assertEquals(GameType.CLICKER, GameType.fromPathName("clicker"))
        assertEquals(GameType.CLICKER, GameType.fromPathName("CLICKER"))
    }

    @Test
    fun `모르는 이름이면 null 이다`() {
        assertNull(GameType.fromPathName("tetris"))
    }

    @Test
    fun `게임마다 경로 이름이 겹치지 않는다`() {
        assertEquals(
            GameType.entries.size,
            GameType.entries
                .map { it.pathName }
                .toSet()
                .size,
        )
    }
}
