package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.PromptPreviewRequest
import com.nexters.gamss.admin.controller.dto.PromptPreviewResponse
import com.nexters.gamss.admin.controller.dto.PromptReplyPreviewRequest
import com.nexters.gamss.admin.controller.dto.PromptReplyPreviewResponse
import com.nexters.gamss.global.response.ApiResponse
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
    description = "저장하기 전 프롬프트로 실제 LLM 생성을 시험한다 (ROLE_ADMIN 필요). DB에 아무것도 저장하지 않는다.",
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
                "프롬프트도, 생성 로그도 저장하지 않으며 실제 비용이 발생합니다. " +
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
                "아무것도 저장하지 않으며 실제 비용이 발생합니다.\n\n" +
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
}
