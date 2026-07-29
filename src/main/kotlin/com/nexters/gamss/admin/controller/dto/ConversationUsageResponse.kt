package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.ConversationUsage
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** 백오피스 '대화방별 사용량' 한 행. 대화방별 메시지 수·카드 생성 여부·소비 토큰(총량·캐시)·예상 비용을 담는다. */
data class ConversationUsageResponse(
    @field:Schema(description = "대화방 id", example = "1024")
    val conversationId: Long,
    @field:Schema(description = "대화방 소유 회원 id", example = "42")
    val memberId: Long,
    @field:Schema(description = "대화방 제목(없으면 null)", example = "오늘의 감정", nullable = true)
    val title: String?,
    @field:Schema(description = "대화방 상태", example = "ENDED")
    val status: String,
    @field:Schema(description = "대화방 생성 시각")
    val createdAt: Instant,
    @field:Schema(description = "유저가 보낸 메시지 수", example = "2")
    val userMessageCount: Long,
    @field:Schema(description = "감정 캐릭터가 보낸 메시지 수", example = "5")
    val characterMessageCount: Long,
    @field:Schema(description = "이 대화방에서 카드가 생성됐는지", example = "true")
    val cardCreated: Boolean,
    @field:Schema(description = "이 대화방에서 소비된 총 토큰(used_tokens 합)", example = "13200")
    val totalTokens: Long,
    @field:Schema(description = "그중 캐시로 처리돼 할인 과금된 토큰", example = "8100")
    val cachedTokens: Long,
    @field:Schema(description = "이 대화방의 예상 비용(USD). 모델별 요금표로 입력·캐시입력·출력을 각각 계산한 합계", example = "0.0142")
    val estimatedCostUsd: Double,
) {
    companion object {
        fun from(usage: ConversationUsage): ConversationUsageResponse =
            ConversationUsageResponse(
                conversationId = usage.conversationId,
                memberId = usage.memberId,
                title = usage.title,
                status = usage.status.name,
                createdAt = usage.createdAt,
                userMessageCount = usage.userMessageCount,
                characterMessageCount = usage.characterMessageCount,
                cardCreated = usage.cardCreated,
                totalTokens = usage.totalTokens,
                cachedTokens = usage.cachedTokens,
                estimatedCostUsd = usage.estimatedCostUsd,
            )
    }
}
