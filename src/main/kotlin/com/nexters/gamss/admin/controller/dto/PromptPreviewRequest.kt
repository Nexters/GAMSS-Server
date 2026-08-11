package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class PromptPreviewRequest(
    @field:Schema(description = "시험할 공통 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "commonPrompt는 20000자 이하여야 합니다.")
    val commonPrompt: String? = null,
    @field:Schema(description = "시험할 댓글 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "commentPrompt는 20000자 이하여야 합니다.")
    val commentPrompt: String? = null,
    @field:Schema(description = "샘플 일기(유저 메시지)", example = "오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다")
    @field:NotBlank(message = "diaryContent는 필수입니다.")
    @field:Size(max = 2000, message = "diaryContent는 2000자 이하여야 합니다.")
    val diaryContent: String,
    @field:Schema(description = "현재 채팅방 임시 요약(선택)", nullable = true)
    @field:Size(max = 2_000, message = "currentConversationSummary는 2000자 이하여야 합니다.")
    val currentConversationSummary: String? = null,
    @field:Schema(description = "등장 캐릭터(1명 이상 필수)")
    @field:NotEmpty(message = "characters는 1개 이상이어야 합니다.")
    val characters: List<EmotionType>,
    @field:Schema(description = "티키타카 개수. 생략 시 0", example = "1", nullable = true)
    val tikitakaCount: Int? = null,
)
