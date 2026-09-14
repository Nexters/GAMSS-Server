package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.prompt.CardMessageWindow
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 공통 프롬프트를 받지 않는 것은 카드 프롬프트가 조립되지 않기 때문이다(단독 사용). */
data class PromptCardPreviewRequest(
    @field:Schema(description = "시험할 카드 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "cardPrompt는 20000자 이하여야 합니다.")
    val cardPrompt: String? = null,
    @field:Schema(description = "대표 감정(어떤 사건을 고를지의 기준)", example = "ANGER")
    @field:NotNull(message = "emotion은 필수입니다.")
    val emotion: EmotionType?,
    // 실제 서비스는 채팅방의 유저 메시지를 넣는다. 한 메시지의 상한은 메시지 저장 요청(SaveMessageRequest)과 같고,
    // 개수 상한은 실제 생성이 한 번에 볼 수 있는 최대치(CardMessageWindow.MAX_MESSAGES)다 — 더 좁히면 짧은 메시지가
    // 많은 대화를 재현할 수 없다.
    @field:Schema(
        description = "유저가 보낸 메시지 목록(시간순). 카드 한 줄의 사실 기준이며 실제 서비스는 채팅방의 유저 메시지를 넣는다",
        example = "[\"오늘 팀장이 자기 할 일을 다 떠넘김\", \"결국 야근함\"]",
    )
    @field:NotEmpty(message = "userMessages는 1개 이상이어야 합니다.")
    @field:Size(max = CardMessageWindow.MAX_MESSAGES, message = "userMessages는 ${CardMessageWindow.MAX_MESSAGES}개 이하여야 합니다.")
    val userMessages: List<
        @NotBlank(message = "메시지는 비어 있을 수 없습니다.")
        @Size(max = 140, message = "메시지 하나는 140자 이하여야 합니다.")
        String,
    >?,
    @field:Schema(
        description = "클라이언트가 만든 대화 요약(선택, 참고용). 비우면 요약이 없는 새벽 배치와 같은 조건으로 시험한다",
        example = "오늘 팀장이 자기 할 일을 다 떠넘김. 야근함.",
        nullable = true,
    )
    @field:Size(max = 2000, message = "summary는 2000자 이하여야 합니다.")
    val summary: String? = null,
)
