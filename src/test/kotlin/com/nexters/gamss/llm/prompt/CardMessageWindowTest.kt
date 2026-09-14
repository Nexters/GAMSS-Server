package com.nexters.gamss.llm.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CardMessageWindow]가 소유한 계약을 검증한다. 감정 분류와 카드 한 줄 생성이 **같은 구간**을 보게
 * 하는 것이 이 객체의 존재 이유라, 여기가 깨지면 카드에 적힌 사건과 그 카드의 감정이 하루의 다른
 * 절반에서 나온다.
 */
class CardMessageWindowTest {
    @Test
    fun `상한을 넘으면 오래된 메시지부터 버리고 시간순은 유지한다`() {
        val messages = (1..30).map { "$it" + "가".repeat(139) }

        val recent = CardMessageWindow.recent(messages)

        assertTrue(recent.size < messages.size, "상한에 걸리지 않으면 이 테스트가 아무것도 지키지 못한다")
        assertEquals(messages.last(), recent.last(), "가장 최근 메시지가 마지막에 남아야 한다")
        assertEquals(recent, recent.sortedBy { messages.indexOf(it) }, "남은 메시지의 순서는 뒤집히면 안 된다")
    }

    @Test
    fun `상한을 혼자 넘기는 메시지 하나뿐이어도 버리지 않는다`() {
        // 그것마저 버리면 그 방은 카드를 만들 근거가 아예 없어진다.
        val recent = CardMessageWindow.recent(listOf("가".repeat(CardMessageWindow.MAX_CHARS * 2)))

        assertEquals(1, recent.size)
    }

    @Test
    fun `1자짜리 메시지로 채우면 MAX_MESSAGES개까지 담긴다`() {
        // 입력 개수를 제한하는 쪽(플레이그라운드 요청)이 이 값을 상한으로 쓴다. 실제 창과 어긋나면 짧은 메시지가
        // 많은 대화를 미리보기로 재현할 수 없다.
        val recent = CardMessageWindow.recent(List(CardMessageWindow.MAX_MESSAGES + 10) { "가" })

        assertEquals(CardMessageWindow.MAX_MESSAGES, recent.size)
    }

    @Test
    fun `불릿으로 감싼 몫까지 세어 짧은 메시지가 많아도 상한을 넘지 않는다`() {
        // 예산이 본문 길이만 재면 짧은 메시지가 많을 때 "- "와 개행 몫이 쌓여 실제 프롬프트가 상한을 넘는다.
        val manyShortMessages = List(3_000) { "가" }

        val recent = CardMessageWindow.recent(manyShortMessages)
        val renderedLength = recent.sumOf { "- $it\n".length }

        assertTrue(renderedLength <= CardMessageWindow.MAX_CHARS, "불릿으로 감싼 길이가 상한을 넘었다: $renderedLength")
    }

    @Test
    fun `공백뿐인 메시지는 빼고 개행은 공백으로 뭉갠다`() {
        // 신뢰할 수 없는 입력이 프롬프트 섹션 구조를 흉내 내지 못하게 하는 것과 같은 이유다.
        val recent = CardMessageWindow.recent(listOf("첫 줄\n둘째 줄", "   ", "마지막"))

        assertEquals(listOf("첫 줄 둘째 줄", "마지막"), recent)
    }

    @Test
    fun `담을 메시지가 없으면 빈 목록이다`() {
        // 예외를 여기서 던지지 않는다 — 그 경우를 어떻게 다룰지는 부르는 쪽이 정한다.
        assertEquals(emptyList(), CardMessageWindow.recent(listOf("  ", "\n")))
    }
}
