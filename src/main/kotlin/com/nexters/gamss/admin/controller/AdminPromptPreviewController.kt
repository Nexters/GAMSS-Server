package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.PromptCardPreviewRequest
import com.nexters.gamss.admin.controller.dto.PromptCardPreviewResponse
import com.nexters.gamss.admin.controller.dto.PromptPreviewRequest
import com.nexters.gamss.admin.controller.dto.PromptPreviewResponse
import com.nexters.gamss.admin.controller.dto.PromptReplyPreviewRequest
import com.nexters.gamss.admin.controller.dto.PromptReplyPreviewResponse
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.llm.preview.CardPreviewCommand
import com.nexters.gamss.llm.preview.PromptPreviewCommand
import com.nexters.gamss.llm.preview.PromptPreviewService
import com.nexters.gamss.llm.preview.ReplyPreviewCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 프롬프트 플레이그라운드",
    description =
        "저장하기 전 프롬프트로 실제 LLM 생성을 시험한다 (ROLE_ADMIN 필요). " +
            "프롬프트는 저장하지 않으며, 비용 추적용 생성 로그(PREVIEW)만 남는다(품질 지표에서 제외).",
)
@RestController
@RequestMapping("/api/admin/llm-settings/prompt/preview")
class AdminPromptPreviewController(
    private val promptPreviewService: PromptPreviewService,
) {
    @Operation(
        summary = "프롬프트 미리보기 생성",
        description =
            "미저장 프롬프트(공통·댓글)와 샘플 일기로 실제 LLM을 호출해 댓글 피드를 생성해봅니다. " +
                "프롬프트는 저장하지 않으며 실제 비용이 발생합니다(비용 추적용 PREVIEW 생성 로그만 기록). " +
                "생성·검증 실패는 오류 필드로 담겨 200으로 내려옵니다(실패 관찰이 목적).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | diaryContent 누락·2000자 초과, 캐릭터·티키타카 조건 위반 |",
    )
    @PostMapping
    fun preview(
        @Valid @RequestBody request: PromptPreviewRequest,
    ): ApiResponse<PromptPreviewResponse> {
        val result =
            promptPreviewService.preview(
                PromptPreviewCommand(
                    commonPrompt = request.commonPrompt,
                    commentPrompt = request.commentPrompt,
                    diaryContent = request.diaryContent,
                    currentConversationSummary = request.currentConversationSummary,
                    pastSummaries = request.pastSummaries ?: emptyList(),
                    characters = request.characters,
                    tikitakaCount = request.tikitakaCount,
                ),
            )
        return ApiResponse.success(PromptPreviewResponse.from(result))
    }

    @Operation(
        summary = "답장 미리보기 생성",
        description =
            "유저가 특정 캐릭터의 댓글에 답장했을 때 그 캐릭터의 재응답을 실제 답글 생성 경로로 시험합니다. " +
                "프롬프트는 저장하지 않으며 실제 비용이 발생합니다(비용 추적용 PREVIEW 생성 로그만 기록).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | 필수값 누락·길이 초과 |",
    )
    @PostMapping("/reply")
    fun previewReply(
        @Valid @RequestBody request: PromptReplyPreviewRequest,
    ): ApiResponse<PromptReplyPreviewResponse> {
        val result =
            promptPreviewService.previewReply(
                ReplyPreviewCommand(
                    commonPrompt = request.commonPrompt,
                    replyPrompt = request.replyPrompt,
                    diaryContent = request.diaryContent,
                    character = checkNotNull(request.character),
                    characterComment = request.characterComment,
                    userReply = request.userReply,
                ),
            )
        return ApiResponse.success(PromptReplyPreviewResponse.from(result))
    }

    @Operation(
        summary = "카드 한 줄 미리보기 생성",
        description =
            "대표 감정과 대화 요약으로 카드에 남을 한 줄을 실제 카드 생성 경로로 시험합니다. " +
                "카드 프롬프트는 공통 프롬프트와 조립되지 않으므로 commonPrompt를 받지 않습니다.\n\n" +
                "자르기 전 원문(rawLine)과 실제 저장될 한 줄(line)을 함께 돌려줍니다 — " +
                "프롬프트의 길이 지시가 지켜지는지, 서버 truncate에 얼마나 기대고 있는지 보기 위한 것입니다.\n\n" +
                "프롬프트는 저장하지 않으며 실제 비용이 발생합니다(비용 추적용 PREVIEW 생성 로그만 기록). " +
                "생성 실패는 오류 필드로 담겨 200으로 내려옵니다(실패 관찰이 목적).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | emotion·summary 누락, 길이 초과 |",
    )
    @PostMapping("/card")
    fun previewCard(
        @Valid @RequestBody request: PromptCardPreviewRequest,
    ): ApiResponse<PromptCardPreviewResponse> {
        val result =
            promptPreviewService.previewCard(
                CardPreviewCommand(
                    cardPrompt = request.cardPrompt,
                    emotion = checkNotNull(request.emotion),
                    summary = request.summary,
                ),
            )
        return ApiResponse.success(PromptCardPreviewResponse.from(result))
    }
}
