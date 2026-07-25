package com.nexters.gamss.conversation.controller

import com.nexters.gamss.admin.controller.dto.PageResponse
import com.nexters.gamss.conversation.controller.dto.CommentGenerationResponse
import com.nexters.gamss.conversation.controller.dto.CommentGenerationStatus
import com.nexters.gamss.conversation.controller.dto.ConversationResponse
import com.nexters.gamss.conversation.controller.dto.ConversationSearchResponse
import com.nexters.gamss.conversation.controller.dto.GenerateCommentsRequest
import com.nexters.gamss.conversation.controller.dto.MessageResponse
import com.nexters.gamss.conversation.controller.dto.ReplyGenerationResponse
import com.nexters.gamss.conversation.controller.dto.SaveMessageRequest
import com.nexters.gamss.conversation.service.CommentGenerationOutcome
import com.nexters.gamss.conversation.service.CommentGenerationService
import com.nexters.gamss.conversation.service.ConversationSearchService
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "대화", description = "감정 기록(채팅방·메시지) 저장·조회 API (모두 로그인 필요)")
@Validated
@RestController
@RequestMapping("/api/conversations")
class ConversationController(
    private val conversationService: ConversationService,
    private val commentGenerationService: CommentGenerationService,
    private val conversationSearchService: ConversationSearchService,
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
            "해당 날짜(00:00 ~ 24:00, KST)에 생성된 채팅방 목록을 반환합니다.\n\n" +
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
        summary = "대화방 검색",
        description =
            "제목(카드 요약) 또는 채팅 내용에 검색어가 포함된 본인 대화방을 최신순으로 조회합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| INVALID_INPUT | 400 | 검색어가 2자 미만 |",
    )
    @GetMapping("/search")
    fun searchConversations(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "검색어(제목·채팅 내용)", example = "짜증")
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ApiResponse<PageResponse<ConversationSearchResponse>> {
        val result = conversationSearchService.search(principal.memberId, keyword, page, size)
        return ApiResponse.success(PageResponse.from(result, ConversationSearchResponse::from))
    }

    @Operation(
        summary = "채팅방 종료",
        description =
            "채팅방을 종료 상태로 만듭니다. 종료된 채팅방에는 더 이상 사용자 메시지를 추가할 수 없습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| CONVERSATION_NOT_FOUND | 404 | 존재하지 않는 채팅방 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |\n" +
                "| CONVERSATION_ALREADY_ENDED | 409 | 이미 종료된 채팅방 |",
    )
    @PostMapping("/{conversationId}/end")
    fun endConversation(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable conversationId: Long,
    ): ApiResponse<ConversationResponse> {
        val conversation = conversationService.endConversation(principal.memberId, conversationId)
        return ApiResponse.success(ConversationResponse.from(conversation))
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

    @Operation(
        summary = "일기(메시지)에 대한 캐릭터 댓글 생성",
        description =
            "일기 메시지에 캐릭터 댓글+티키타카 생성을 요청합니다. 멱등한 엔드포인트로, " +
                "재요청이 곧 결과 조회를 겸합니다 — GENERATING이면 잠시 후 같은 요청을 다시 보내면 됩니다.\n\n" +
                "**status 값**\n\n" +
                "| status | 의미 |\n" +
                "|---|---|\n" +
                "| GENERATING | 생성 중(직접 트리거했거나 다른 요청이 먼저 선점) |\n" +
                "| DONE | 생성 완료, comments에 결과 포함 |\n" +
                "| FAILED | 재시도까지 실패. 다시 요청하면 재시도됨 |\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| INVALID_INPUT | 400 | messageId 누락 |\n" +
                "| MESSAGE_NOT_FOUND | 404 | 존재하지 않는 메시지 |\n" +
                "| INVALID_COMMENT_TARGET | 400 | 일기(사용자) 메시지가 아님 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |",
    )
    @PostMapping("/messages/comments")
    fun generateComments(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: GenerateCommentsRequest,
    ): ApiResponse<CommentGenerationResponse> {
        val result = commentGenerationService.generateComments(principal.memberId, checkNotNull(request.messageId))
        val status =
            when (result.outcome) {
                CommentGenerationOutcome.GENERATING -> CommentGenerationStatus.GENERATING
                CommentGenerationOutcome.DONE -> CommentGenerationStatus.DONE
                CommentGenerationOutcome.FAILED -> CommentGenerationStatus.FAILED
            }
        val comments = if (result.outcome == CommentGenerationOutcome.DONE) result.comments.map { MessageResponse.from(it) } else null
        return ApiResponse.success(CommentGenerationResponse(status, comments, result.usedTokens))
    }

    @Operation(
        summary = "캐릭터 댓글에 대한 답글 생성",
        description =
            "유저가 캐릭터 댓글에 단 답글(messageId)에 그 캐릭터가 다시 응답하도록 요청합니다. 멱등한 엔드포인트로, " +
                "재요청이 곧 결과 조회를 겸합니다 — GENERATING이면 잠시 후 같은 요청을 다시 보내면 됩니다.\n\n" +
                "**status 값**\n\n" +
                "| status | 의미 |\n" +
                "|---|---|\n" +
                "| GENERATING | 생성 중(직접 트리거했거나 다른 요청이 먼저 선점) |\n" +
                "| DONE | 생성 완료, comment에 결과 포함 |\n" +
                "| FAILED | 재시도까지 실패. 다시 요청하면 재시도됨 |\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요 |\n" +
                "| MESSAGE_NOT_FOUND | 404 | 존재하지 않는 메시지 |\n" +
                "| INVALID_COMMENT_TARGET | 400 | 유저 답글이 아니거나, 답글 대상이 캐릭터 댓글이 아님 |\n" +
                "| CONVERSATION_NOT_FOUND | 404 | 존재하지 않는 채팅방 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |",
    )
    @PostMapping("/messages/comments/{messageId}")
    fun generateReplyComments(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable messageId: Long,
    ): ApiResponse<ReplyGenerationResponse> {
        val result = commentGenerationService.generateReplyComment(principal.memberId, messageId)
        val status =
            when (result.outcome) {
                CommentGenerationOutcome.GENERATING -> CommentGenerationStatus.GENERATING
                CommentGenerationOutcome.DONE -> CommentGenerationStatus.DONE
                CommentGenerationOutcome.FAILED -> CommentGenerationStatus.FAILED
            }
        val comment = if (result.outcome == CommentGenerationOutcome.DONE) MessageResponse.from(result.message!!) else null
        return ApiResponse.success(ReplyGenerationResponse(status, comment, result.usedTokens))
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100L
    }
}
