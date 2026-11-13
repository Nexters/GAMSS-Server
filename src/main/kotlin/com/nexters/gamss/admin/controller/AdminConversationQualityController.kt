package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.HumanVerdictRequest
import com.nexters.gamss.admin.service.ConversationQualityService
import com.nexters.gamss.admin.service.HumanVerdictResult
import com.nexters.gamss.admin.service.LabelingNext
import com.nexters.gamss.admin.service.PlaygroundInput
import com.nexters.gamss.admin.service.ResponseQualityStats
import com.nexters.gamss.admin.service.ResponseQualityStatsService
import com.nexters.gamss.admin.service.ResponseRow
import com.nexters.gamss.admin.service.ThreadView
import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.PageResponse
import com.nexters.gamss.global.security.AdminPrincipal
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.HumanState
import com.nexters.gamss.quality.domain.ResponseFilter
import com.nexters.gamss.quality.service.CalibrationReport
import com.nexters.gamss.quality.service.CalibrationRun
import com.nexters.gamss.quality.service.QualityCalibrationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@Tag(name = "백오피스 대화 품질", description = "캐릭터 응답의 어색함 판정, 대화 원문 열람 (ROLE_ADMIN 필요). 원문을 열면 열람 기록이 남는다.")
@Validated
@RestController
@RequestMapping("/api/admin")
class AdminConversationQualityController(
    private val conversationQualityService: ConversationQualityService,
    private val responseQualityStatsService: ResponseQualityStatsService,
    private val qualityCalibrationService: QualityCalibrationService,
) {
    @Operation(
        summary = "대화 원문",
        description = "대화를 메시지 순서대로 돌려주고 캐릭터 응답마다 판정을 붙인다. blind=true 면 내가 판정하지 않은 응답의 AI 판정을 가린다.",
    )
    @GetMapping("/conversations/{conversationId}/thread")
    fun thread(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @PathVariable conversationId: Long,
        @RequestParam(defaultValue = "false") blind: Boolean,
    ): ApiResponse<ThreadView> = ApiResponse.success(conversationQualityService.thread(conversationId, principal.email, blind))

    @Operation(summary = "대화 품질 지표", description = "최근 days 일간 어색한 응답 비율, 캐릭터별 비율, 유형 순위, 일별 추이, 설정 변경 시점, AI-사람 일치율.")
    @GetMapping("/conversation-quality/stats")
    fun stats(
        @RequestParam(defaultValue = "14") @Min(1) @Max(MAX_DAYS) days: Int,
    ): ApiResponse<ResponseQualityStats> = ApiResponse.success(responseQualityStatsService.stats(days))

    @Operation(
        summary = "응답 목록",
        description = "캐릭터 응답을 최신순으로 돌려준다. aiVerdict 에 NONE 을 주면 아직 채점되지 않은 응답만 본다.",
    )
    @GetMapping("/conversation-quality/responses")
    fun responses(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) emotionType: EmotionType?,
        @RequestParam(required = false) kind: ResponseKind?,
        @RequestParam(required = false) aiVerdict: String?,
        @RequestParam(required = false) awkwardType: AwkwardType?,
        @RequestParam(required = false) promptVersion: Int?,
        @RequestParam(defaultValue = "ALL") humanState: HumanState,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ApiResponse<PageResponse<ResponseRow>> {
        val filter =
            ResponseFilter(
                from = from.atStartOfDay(ZONE).toInstant(),
                to = to.plusDays(1).atStartOfDay(ZONE).toInstant(),
                adminEmail = principal.email,
                emotionType = emotionType,
                kind = kind,
                aiVerdict = aiVerdict?.takeIf { it.isNotBlank() },
                awkwardType = awkwardType,
                promptVersion = promptVersion,
                humanState = humanState,
            )
        val result = conversationQualityService.responses(filter, PageRequest.of(page, size))
        return ApiResponse.success(PageResponse.from(result) { it })
    }

    @Operation(summary = "판정하기 다음 대상", description = "내가 아직 판정하지 않은 응답 하나. 캐릭터와 응답 종류가 고르게 섞이도록 고른다.")
    @GetMapping("/conversation-quality/labeling/next")
    fun nextLabeling(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @RequestParam(required = false) exclude: List<Long>?,
    ): ApiResponse<LabelingNext> = ApiResponse.success(conversationQualityService.nextLabeling(principal.email, exclude.orEmpty()))

    @Operation(summary = "사람 판정 저장", description = "내 판정을 저장(덮어쓰기)하고, 가려 두었던 AI 판정을 함께 돌려준다.")
    @PutMapping("/conversation-quality/responses/{messageId}/human-verdict")
    fun saveHumanVerdict(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @PathVariable messageId: Long,
        @Valid @RequestBody request: HumanVerdictRequest,
    ): ApiResponse<HumanVerdictResult> =
        ApiResponse.success(
            conversationQualityService.saveHumanVerdict(
                principal.email,
                messageId,
                checkNotNull(request.verdict),
                request.awkwardTypes,
                request.memo,
            ),
        )

    @Operation(summary = "실험실 입력", description = "이 응답을 만든 입력(일기, 캐릭터, 답장 등)을 프롬프트 실험실에 채울 형태로 돌려준다.")
    @GetMapping("/conversation-quality/responses/{messageId}/playground-input")
    fun playgroundInput(
        @PathVariable messageId: Long,
    ): ApiResponse<PlaygroundInput> = ApiResponse.success(conversationQualityService.playgroundInput(messageId))

    @Operation(summary = "정답 세트 채점 시작", description = "사람 판정이 있는 응답을 지금 채점 설정으로 채점한다. 백그라운드에서 돌고, 이미 돌고 있으면 그 상태를 돌려준다.")
    @PostMapping("/conversation-quality/calibration")
    fun startCalibration(): ApiResponse<CalibrationRun> = ApiResponse.success(qualityCalibrationService.start())

    @Operation(summary = "정답 세트 채점 결과", description = "진행 상태와, 지금 채점 설정 기준의 AI-사람 일치율, 유형별 놓친 비율과 과잉 지적 비율, 사람끼리 일치율.")
    @GetMapping("/conversation-quality/calibration")
    fun calibration(): ApiResponse<CalibrationStatus> =
        ApiResponse.success(CalibrationStatus(qualityCalibrationService.currentRun(), qualityCalibrationService.report()))

    data class CalibrationStatus(
        val run: CalibrationRun?,
        val report: CalibrationReport,
    )

    companion object {
        private const val MAX_DAYS = 90L
        private const val MAX_PAGE_SIZE = 100L
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
