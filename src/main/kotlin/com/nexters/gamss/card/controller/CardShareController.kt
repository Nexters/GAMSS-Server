package com.nexters.gamss.card.controller

import com.nexters.gamss.card.controller.dto.CardShareLinkResponse
import com.nexters.gamss.card.controller.dto.SharedCardResponse
import com.nexters.gamss.card.service.CardShareService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "카드 공유", description = "감정 카드 공유 링크 API")
@RestController
@RequestMapping("/api/cards")
class CardShareController(
    private val cardShareService: CardShareService,
) {
    @Operation(
        summary = "카드 공유 링크 발급 (로그인 필요)",
        description =
            "본인 카드의 공유 링크를 발급합니다. 앱은 받은 URL 을 그대로 공유 시트에 넘기면 됩니다.\n\n" +
                "**같은 카드는 몇 번을 요청해도 같은 링크입니다.** 다시 눌렀다고 링크가 바뀌면 " +
                "앞서 보낸 링크가 죽기 때문입니다.\n\n" +
                "링크를 연 사람이 앱을 갖고 있으면 앱이 열리고, 없으면 카드가 보이는 웹 페이지가 뜹니다. " +
                "링크에는 카드 ID 가 아니라 추측할 수 없는 토큰이 들어갑니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| CARD_NOT_FOUND | 404 | 존재하지 않는 카드 |\n" +
                "| CARD_ACCESS_DENIED | 403 | 본인의 카드가 아님 |\n" +
                "| CARD_ALREADY_DELETED | 409 | 이미 삭제된 카드 — 링크를 만들어도 열리지 않는다 |",
    )
    @PostMapping("/{cardId}/share")
    fun issueShareLink(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Parameter(description = "공유할 카드 ID", example = "1")
        @PathVariable cardId: Long,
    ): ApiResponse<CardShareLinkResponse> {
        val shareUrl = cardShareService.issueShareLink(principal.memberId, cardId)
        return ApiResponse.success(CardShareLinkResponse(shareUrl))
    }

    @Operation(
        summary = "공유된 카드 조회 (로그인 불필요)",
        description =
            "공유 링크(`gamss.kr/c/{shareToken}`)를 연 사람에게 보여줄 카드입니다. " +
                "**토큰을 아는 것이 곧 볼 권한**이므로 인증하지 않습니다.\n\n" +
                "누가 쓴 카드인지 알 수 없도록 소유자와 식별자(카드·채팅방 ID)는 담지 않습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| CARD_NOT_FOUND | 404 | 없는 토큰, 형식이 틀린 토큰, 또는 글쓴이가 지운 카드 " +
                "— 셋을 구분하지 않는다 |",
    )
    @GetMapping("/shared/{shareToken}")
    fun getSharedCard(
        @Parameter(description = "공유 토큰(22자)", example = "Zm9vYmFyYmF6cXV4MTIzNA")
        @PathVariable shareToken: String,
    ): ApiResponse<SharedCardResponse> {
        val card = cardShareService.getSharedCard(shareToken)
        return ApiResponse.success(SharedCardResponse.from(card))
    }
}
