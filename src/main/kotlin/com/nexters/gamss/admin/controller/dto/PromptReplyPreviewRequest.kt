package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class PromptReplyPreviewRequest(
    @field:Schema(description = "시험할 공통 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    val commonPrompt: String? = null,
    @field:Schema(description = "시험할 답글 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    val replyPrompt: String? = null,
    @field:Schema(description = "원본 일기(답장 맥락)", example = "오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다")
    @field:NotBlank(message = "diaryContent는 필수입니다.")
    @field:Size(max = 2000, message = "diaryContent는 2000자 이하여야 합니다.")
    val diaryContent: String,
    @field:Schema(description = "답장 대상 캐릭터", example = "ANGER")
    @field:NotNull(message = "character는 필수입니다.")
    val character: EmotionType?,
    @field:Schema(description = "그 캐릭터가 남겼던 댓글")
    @field:NotBlank(message = "characterComment는 필수입니다.")
    val characterComment: String,
    @field:Schema(description = "유저의 답장", example = "그치? 나 잘못한 거 없지?")
    @field:NotBlank(message = "userReply는 필수입니다.")
    @field:Size(max = 2000, message = "userReply는 2000자 이하여야 합니다.")
    val userReply: String,
)
