package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PromptPreviewRequest(
    @field:Schema(description = "시험할 공통 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    val commonPrompt: String? = null,
    @field:Schema(description = "시험할 댓글 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    val commentPrompt: String? = null,
    @field:Schema(description = "샘플 일기(유저 메시지)", example = "오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다")
    @field:NotBlank(message = "diaryContent는 필수입니다.")
    @field:Size(max = 2000, message = "diaryContent는 2000자 이하여야 합니다.")
    val diaryContent: String,
    @field:Schema(description = "현재 채팅방 임시 요약(선택)", nullable = true)
    val currentConversationSummary: String? = null,
    @field:Schema(description = "등장 캐릭터 고정값(선택). null이면 실제 생성처럼 서버가 무작위 선택", nullable = true)
    val characters: List<EmotionType>? = null,
    @field:Schema(description = "티키타카 개수 고정값(선택). characters 지정 시에만 의미 있음", example = "1", nullable = true)
    val tikitakaCount: Int? = null,
)
