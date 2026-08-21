package com.nexters.gamss.card.controller.dto

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.CardSummary
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.ZoneId

/**
 * 공유 링크로 열리는 카드. **인증 없이 나가는 유일한 카드 응답**이다.
 *
 * [CardResponse] 와 달리 식별자를 담지 않는다 — 카드·채팅방 ID 는 AUTO_INCREMENT 라, 링크 하나를
 * 받은 사람에게 그 번호를 알려주면 다른 카드의 번호를 짐작할 실마리가 된다. 소유자([Card.memberId])도
 * 마찬가지 이유로 담지 않으며, 링크를 받은 사람은 누가 쓴 카드인지 알 수 없다.
 *
 * 화면에 그릴 것만 남긴다 — 캐릭터와 색을 고르는 [emotion], 한 줄 [summary], 그리고 날짜다.
 */
data class SharedCardResponse(
    @field:Schema(description = "대표 감정", example = "ANGER")
    val emotion: String,
    @field:Schema(description = "대표 감정 한글 라벨", example = "분노")
    val emotionLabel: String,
    @field:Schema(
        description = "카드에 남는 한 줄(공백 포함 50자 이하)",
        example = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
    )
    val summary: String,
    @field:Schema(description = "카드가 속한 날짜(대화 생성일 기준, KST)", example = "2026-07-23")
    val date: LocalDate,
) {
    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")

        fun from(card: Card): SharedCardResponse =
            SharedCardResponse(
                emotion = card.emotion.name,
                emotionLabel = card.emotion.label,
                // 개편 이전 카드는 원본(최대 2000자·개행 포함)이 그대로 들어 있다 —
                // [CardResponse] 와 같은 이유로 읽는 쪽에서 한 줄 계약을 지킨다.
                summary = CardSummary.normalize(card.summary),
                date = card.conversationCreatedAt.atZone(ZONE).toLocalDate(),
            )
    }
}
