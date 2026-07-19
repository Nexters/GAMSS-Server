package com.nexters.gamss.conversation.controller

import com.nexters.gamss.conversation.controller.dto.ConversationResponse
import com.nexters.gamss.conversation.controller.dto.MessageResponse
import com.nexters.gamss.conversation.controller.dto.SaveMessageRequest
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "대화", description = "감정 기록(채팅방·메시지) 저장·조회 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/conversations")
class ConversationController(
    private val conversationService: ConversationService,
) {
    @Operation(
        summary = "감정 기록 저장",
        description =
            "사용자 메시지를 저장합니다. conversationId가 없으면 새 채팅방을 만들고, " +
                "있으면 해당 채팅방에 이어서 저장합니다. 응답의 conversationId로 대화를 이어갈 수 있습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| INVALID_INPUT | 400 | content 누락·140자 초과, 또는 잘못된 답장 대상 |\n" +
                "| CONVERSATION_NOT_FOUND | 404 | 존재하지 않는 채팅방 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |",
    )
    @PostMapping("/messages")
    fun saveMessage(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: SaveMessageRequest,
    ): ApiResponse<MessageResponse> {
        val message =
            conversationService.saveUserMessage(
                memberId = principal.memberId,
                conversationId = request.conversationId,
                content = request.content,
                repliesToMessageId = request.repliesToMessageId,
            )
        return ApiResponse.success(MessageResponse.from(message))
    }

    @Operation(
        summary = "날짜별 채팅방 목록 조회",
        description =
            "서비스상 하루(기본 06:00 ~ 익일 06:00, KST)에 생성된 채팅방 목록을 반환합니다. " +
                "예: 새벽 2시에 만든 채팅방은 전날 목록에 포함됩니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| INVALID_INPUT | 400 | date 누락 또는 형식 오류 (yyyy-MM-dd) |",
    )
    @GetMapping
    fun getConversations(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-07-19")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ApiResponse<List<ConversationResponse>> {
        val conversations = conversationService.getConversations(principal.memberId, date)
        return ApiResponse.success(conversations.map { ConversationResponse.from(it) })
    }

    @Operation(
        summary = "채팅방 메시지 전체 조회",
        description =
            "채팅방의 메시지를 작성순으로 반환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| CONVERSATION_NOT_FOUND | 404 | 존재하지 않는 채팅방 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |",
    )
    @GetMapping("/{conversationId}/messages")
    fun getMessages(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable conversationId: Long,
    ): ApiResponse<List<MessageResponse>> {
        val messages = conversationService.getMessages(principal.memberId, conversationId)
        return ApiResponse.success(messages.map { MessageResponse.from(it) })
    }
}
