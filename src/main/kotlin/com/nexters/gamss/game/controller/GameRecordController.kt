package com.nexters.gamss.game.controller

import com.nexters.gamss.game.controller.dto.GameRankingResponse
import com.nexters.gamss.game.controller.dto.GameRecordResponse
import com.nexters.gamss.game.controller.dto.RecordGameRequest
import com.nexters.gamss.game.domain.GameType
import com.nexters.gamss.game.service.GameRankingService
import com.nexters.gamss.game.service.GameRecordService
import com.nexters.gamss.game.service.GameRecordSort
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.PageResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "게임 기록", description = "미니게임 점수 저장·조회 API (모두 로그인 필요). 게임 진행은 클라이언트가 한다")
@Validated
@RestController
@RequestMapping("/api/games/{gameType}/records")
class GameRecordController(
    private val gameRecordService: GameRecordService,
    private val gameRankingService: GameRankingService,
) {
    @Operation(
        summary = "게임 기록 저장",
        description =
            "한 판의 점수를 저장합니다. 클리커(`clicker`)의 점수는 제한 시간 안의 터치 횟수입니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 - 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | 지원하지 않는 게임 또는 score 누락 |\n" +
                "| INVALID_GAME_SCORE | 400 | 음수이거나 상한(클리커 99,999)을 넘는 점수 |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |",
    )
    @PostMapping
    fun record(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "게임 종류", example = "clicker") @PathVariable gameType: GameType,
        @Valid @RequestBody request: RecordGameRequest,
    ): ApiResponse<GameRecordResponse> {
        val record = gameRecordService.record(principal.memberId, gameType, request.score!!)
        return ApiResponse.success(GameRecordResponse.from(record))
    }

    @Operation(
        summary = "내 게임 기록 조회",
        description =
            "본인 기록을 정렬해 페이지로 조회합니다.\n\n" +
                "- `LATEST`: 최신순(기본)\n" +
                "- `SCORE_DESC`: 점수 높은 순\n" +
                "- `SCORE_ASC`: 점수 낮은 순\n\n" +
                "점수가 같으면 최신 기록이 먼저 옵니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 - 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | 지원하지 않는 게임·정렬 또는 잘못된 페이지 값 |",
    )
    @GetMapping("/me")
    fun getMyRecords(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "게임 종류", example = "clicker") @PathVariable gameType: GameType,
        @RequestParam(defaultValue = "LATEST") sort: GameRecordSort,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ApiResponse<PageResponse<GameRecordResponse>> {
        val records = gameRecordService.getMyRecords(principal.memberId, gameType, sort, page, size)
        return ApiResponse.success(PageResponse.from(records, GameRecordResponse::from))
    }

    @Operation(
        summary = "게임 랭킹",
        description =
            "회원마다 최고 기록 하나씩을 정렬해 페이지로 조회합니다. 같은 최고 점수를 여러 번 냈다면 " +
                "먼저 달성한 기록이 쓰입니다. 탈퇴한 회원은 보이지 않습니다.\n\n" +
                "정렬 값은 내 기록 조회와 같고 기본은 `SCORE_DESC` 입니다. `LATEST` 는 최고 기록을 세운 시각 순입니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 - 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | 지원하지 않는 게임·정렬 또는 잘못된 페이지 값 |",
    )
    @GetMapping
    fun getRanking(
        @Parameter(description = "게임 종류", example = "clicker") @PathVariable gameType: GameType,
        @RequestParam(defaultValue = "SCORE_DESC") sort: GameRecordSort,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
    ): ApiResponse<PageResponse<GameRankingResponse>> {
        val ranking = gameRankingService.getRanking(gameType, sort, page, size)
        return ApiResponse.success(PageResponse.from(ranking, GameRankingResponse::from))
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100L
    }
}
