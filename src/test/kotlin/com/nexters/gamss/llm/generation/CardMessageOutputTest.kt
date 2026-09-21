package com.nexters.gamss.llm.generation

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 출력 객체가 스스로 지키는 불변식. 생성기 구현은 교체 가능해서 파서의 검증만 믿을 수 없다 — 빈 한 줄이
 * 성공으로 통과하면 카드 생성이 재시도 블록 밖에서 터져 대화방 상태가 PENDING으로 남는다.
 */
class CardMessageOutputTest {
    @Test
    fun `EVENT 판정인데 한 줄이 비어 있으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { CardMessageOutput(" \n ", usedTokens = 10, cachedTokens = 0) }
    }

    @Test
    fun `EVENT 판정인데 한 줄이 없으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { CardMessageOutput(null, usedTokens = 10, cachedTokens = 0) }
    }

    @Test
    fun `NONSENSE 판정인데 한 줄이 있으면 만들 수 없다`() {
        // 쓸 한 줄이 없다는 판정과 한 줄이 함께 오면 부르는 쪽이 어느 쪽을 믿어야 할지 정할 수 없다.
        assertFailsWith<IllegalArgumentException> {
            CardMessageOutput("팀장이 자기 할 일을 다 떠넘겼어요", usedTokens = 10, cachedTokens = 0, kind = CardLineKind.NONSENSE)
        }
    }
}
