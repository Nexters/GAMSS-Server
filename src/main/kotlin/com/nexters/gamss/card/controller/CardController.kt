package com.nexters.gamss.card.controller

import com.nexters.gamss.card.controller.dto.CardCalendarResponse
import com.nexters.gamss.card.controller.dto.CardDeleteResponse
import com.nexters.gamss.card.controller.dto.CardResponse
import com.nexters.gamss.card.controller.dto.CreateCardRequest
import com.nexters.gamss.card.service.CardService
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth

@Tag(name = "카드", description = "대화 종료 시 생성되는 감정 카드 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/cards")
class CardController(
    private val cardService: CardService,
) {
    @Operation(
        summary = "카드 생성",
        description =
            "종료된 채팅방에 대해 그날 있었던 일을 한 줄(공백 포함 50자 이하)로 요약해 카드를 만듭니다. " +
                "요약은 클라이언트가 보낸 원본을 서버 LLM이 다듬은 값이며, 원본은 채팅방에 그대로 남습니다. " +
                "감정은 클라이언트가 준 값을 그대로 대표 감정으로 쓰고, **emotion을 생략하면** " +
                "서버가 유저가 보낸 메시지들만 보고 감정을 추출해 채웁니다(클라이언트 추출 실패 시 폴백).\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | conversationId·summary 누락 또는 형식 오류(emotion은 생략 가능) |\n" +
                "| CONVERSATION_NOT_FOUND | 404 | 존재하지 않는 채팅방 |\n" +
                "| CONVERSATION_ACCESS_DENIED | 403 | 본인 채팅방이 아님 |\n" +
                "| CONVERSATION_ALREADY_DELETED | 409 | 이미 삭제된 채팅방 |\n" +
                "| CONVERSATION_NOT_ENDED | 409 | 종료되지 않은 채팅방 |\n" +
                "| CARD_ALREADY_EXISTS | 409 | 이미 카드가 생성된 채팅방 |\n" +
                "| CARD_GENERATION_IN_PROGRESS | 409 | 카드 생성 중(재시도 가능) |\n" +
                "| CARD_GENERATION_FAILED | 503 | 감정 추출·카드 한 줄 생성 실패(재시도 가능) |",
    )
    @PostMapping
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: CreateCardRequest,
    ): ApiResponse<CardResponse> {
        val card =
            cardService.createCard(
                memberId = principal.memberId,
                conversationId = checkNotNull(request.conversationId),
                emotion = request.emotion,
                summary = checkNotNull(request.summary),
            )
        return ApiResponse.success(CardResponse.from(card))
    }

    @Operation(
        summary = "날짜별 카드 조회",
        description =
            "해당 날짜(대화 생성일 기준, KST)에 속한 카드 목록을 반환합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | date 누락 또는 형식 오류 (yyyy-MM-dd) |",
    )
    @GetMapping
    fun getByDate(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-07-23")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ApiResponse<List<CardResponse>> {
        val cards = cardService.getCardsByDate(principal.memberId, date)
        return ApiResponse.success(cards.map { CardResponse.from(it) })
    }

    @Operation(
        summary = "월별 카드 조회(캘린더)",
        description =
            "해당 월(대화 생성일 기준, KST)의 날짜별 대표 감정 목록만 반환합니다(캘린더 표시용). " +
                "한 줄 요약 등 상세는 날짜를 눌러 날짜별 조회로 확인합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | yearMonth 누락 또는 형식 오류 (yyyy-MM) |",
    )
    @GetMapping("/monthly")
    fun getByMonth(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "조회할 월 (yyyy-MM)", example = "2026-07")
        @RequestParam
        @DateTimeFormat(pattern = "yyyy-MM") yearMonth: YearMonth,
    ): ApiResponse<List<CardCalendarResponse>> {
        val cards = cardService.getCardsByMonth(principal.memberId, yearMonth)
        return ApiResponse.success(CardCalendarResponse.listFrom(cards))
    }

    @Operation(
        summary = "월별 감정별 카드 조회",
        description =
            "해당 월(대화 생성일 기준, KST)의 카드 중 지정한 감정인 것만 **내용까지 담아** 최신순으로 " +
                "반환합니다. 감정 탭에서 한 달치를 몰아 볼 때 씁니다 — 월별 캘린더 조회로 날짜만 받아 " +
                "날짜별 조회를 날짜마다 다시 부를 필요가 없습니다.\n\n" +
                "- 응답 형태는 날짜별 조회와 같습니다.\n" +
                "- 날짜별·월별 조회와 **같은 카드만 보입니다** — 삭제한 카드와 삭제된 채팅방의 카드는 " +
                "제외됩니다.\n" +
                "- 정렬은 **최신순**입니다(같은 시각이면 id 내림차순). 날짜별·월별 조회와 순서가 반대입니다.\n" +
                "- 대상이 0장이면 빈 배열로 **성공**합니다.\n" +
                "- 페이지네이션이 없습니다. 한 달·한 감정 분량을 한 번에 돌려줍니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | yearMonth 누락·형식 오류(yyyy-MM) 또는 지원하지 않는 감정 값 |",
    )
    @GetMapping("/monthly/emotions/{emotion}")
    fun getByMonthAndEmotion(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "조회할 카드의 감정", example = "ANGER")
        @PathVariable emotion: EmotionType,
        @Parameter(description = "조회할 월 (yyyy-MM)", example = "2026-07")
        @RequestParam
        @DateTimeFormat(pattern = "yyyy-MM") yearMonth: YearMonth,
    ): ApiResponse<List<CardResponse>> {
        val cards = cardService.getCardsByMonthAndEmotion(principal.memberId, yearMonth, emotion)
        return ApiResponse.success(cards.map { CardResponse.from(it) })
    }

    @Operation(
        summary = "카드 단건 조회",
        description =
            "id 로 본인 카드 한 장을 조회합니다. 날짜를 몰라도 카드 상세를 열 수 있습니다.\n\n" +
                "- 날짜별·월별 조회와 **같은 카드만 보입니다** — 삭제한 카드와 삭제된 채팅방의 " +
                "카드는 `CARD_NOT_FOUND` 로 응답합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | cardId 가 숫자가 아님 |\n" +
                "| CARD_NOT_FOUND | 404 | 존재하지 않거나 이미 사라진 카드 |\n" +
                "| CARD_ACCESS_DENIED | 403 | 본인 카드가 아님 |",
    )
    @GetMapping("/{cardId}")
    fun getById(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable cardId: Long,
    ): ApiResponse<CardResponse> {
        val card = cardService.getCard(principal.memberId, cardId)
        return ApiResponse.success(CardResponse.from(card))
    }

    @Operation(
        summary = "카드 삭제",
        description =
            "본인 카드를 삭제합니다. 삭제된 카드는 날짜별·월별 조회에서 더 이상 보이지 않습니다.\n\n" +
                "- **되돌릴 수 없습니다.** 채팅방당 카드는 하나뿐이고, 삭제 후 같은 채팅방에 카드를 " +
                "다시 만들 수 없습니다(`CARD_ALREADY_EXISTS`).\n" +
                "- **카드가 나온 채팅방도 함께 삭제됩니다.** 그 채팅방은 목록·검색에서 사라지고 " +
                "대화를 이어갈 수 없습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| CARD_NOT_FOUND | 404 | 존재하지 않는 카드 |\n" +
                "| CARD_ACCESS_DENIED | 403 | 본인 카드가 아님 |\n" +
                "| CARD_ALREADY_DELETED | 409 | 이미 삭제된 카드 |",
    )
    @DeleteMapping("/{cardId}")
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable cardId: Long,
    ): ApiResponse<Unit> {
        cardService.deleteCard(principal.memberId, cardId)
        return ApiResponse.success()
    }

    @Operation(
        summary = "감정별 카드 일괄 삭제",
        description =
            "본인 카드 중 해당 감정인 것을 한 번에 삭제하고, 삭제된 장수를 돌려줍니다.\n\n" +
                "- 대상이 0장이어도 **성공**합니다(`deletedCount: 0`). 연속 호출해도 안전합니다.\n" +
                "- 이미 삭제한 카드는 다시 세지 않습니다.\n" +
                "- **되돌릴 수 없습니다.** 삭제한 채팅방에는 카드를 다시 만들 수 없습니다.\n" +
                "- **카드가 나온 채팅방도 모두 함께 삭제됩니다.** 그 채팅방들은 목록·검색에서 " +
                "사라지고 대화를 이어갈 수 없습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | 지원하지 않는 감정 값 |",
    )
    @DeleteMapping("/emotions/{emotion}")
    fun deleteByEmotion(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "삭제할 카드의 감정", example = "ANGER")
        @PathVariable emotion: EmotionType,
    ): ApiResponse<CardDeleteResponse> {
        val deletedCount = cardService.deleteCardsByEmotion(principal.memberId, emotion)
        return ApiResponse.success(CardDeleteResponse(deletedCount))
    }

    @Operation(
        summary = "카드 전체 삭제",
        description =
            "본인 카드를 전부 삭제하고, 삭제된 장수를 돌려줍니다.\n\n" +
                "- **되돌릴 수 없습니다.** 삭제한 채팅방에는 카드를 다시 만들 수 없으니, " +
                "호출 전에 사용자 확인 단계를 두는 것을 권장합니다.\n" +
                "- 대상이 0장이어도 **성공**합니다(`deletedCount: 0`). 연속 호출해도 안전합니다.\n" +
                "- 이미 삭제한 카드는 다시 세지 않습니다.\n" +
                "- **카드가 나온 채팅방도 모두 함께 삭제됩니다.** 그 채팅방들은 목록·검색에서 " +
                "사라지고 대화를 이어갈 수 없습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |",
    )
    @DeleteMapping
    fun deleteAll(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<CardDeleteResponse> {
        val deletedCount = cardService.deleteAllCards(principal.memberId)
        return ApiResponse.success(CardDeleteResponse(deletedCount))
    }
}
