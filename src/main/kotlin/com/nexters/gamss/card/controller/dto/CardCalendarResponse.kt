package com.nexters.gamss.card.controller.dto

import com.nexters.gamss.card.domain.Card
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.ZoneId

/**
 * 캘린더(월별) 화면용 경량 응답. 날짜별로 그날 만들어진 카드의 대표 감정 목록만 담는다
 * (한 줄 요약 등 상세는 제외 — 날짜를 눌러 날짜별 조회로 확인).
 */
data class CardCalendarResponse(
    @field:Schema(description = "날짜 (KST)", example = "2026-07-23")
    val date: LocalDate,
    @field:Schema(description = "그날 카드들의 대표 감정 목록(카드 생성순, 중복 허용)", example = "[\"ANGER\", \"ANXIETY\"]")
    val emotions: List<String>,
) {
    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")

        /** 카드들을 대화 생성일(KST)별로 묶어 날짜 오름차순의 캘린더 응답으로 만든다. */
        fun listFrom(cards: List<Card>): List<CardCalendarResponse> =
            cards
                .groupBy { it.conversationCreatedAt.atZone(ZONE).toLocalDate() }
                .toSortedMap()
                .map { (date, dayCards) -> CardCalendarResponse(date, dayCards.map { it.emotion.name }) }
    }
}
