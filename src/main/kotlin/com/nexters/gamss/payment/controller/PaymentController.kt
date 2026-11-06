package com.nexters.gamss.payment.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.payment.controller.dto.PaymentAccountTokenResponse
import com.nexters.gamss.payment.controller.dto.TokenProductResponse
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.payment.service.TokenProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "유료 토큰 인앱결제 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val tokenProductService: TokenProductService,
    private val paymentAccountService: PaymentAccountService,
) {
    @Operation(
        summary = "판매 상품 목록 조회",
        description =
            "지금 판매하는 토큰 상품을 지급량 오름차순으로 반환합니다.\n\n" +
                "- 가격은 내려주지 않습니다. 각 스토어 SDK 에서 이 productId 로 현지 통화 가격을 가져옵니다.\n" +
                "- 지급량은 서버가 정합니다. 구매 검증 요청에 수량이나 금액을 보내도 쓰지 않습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |",
    )
    @GetMapping("/products")
    fun products(): ApiResponse<List<TokenProductResponse>> =
        ApiResponse.success(tokenProductService.activeProducts().map { TokenProductResponse.from(it) })

    @Operation(
        summary = "구매 계정 토큰 조회",
        description =
            "구매할 때 스토어에 함께 넘길 계정 토큰(UUID)을 반환합니다. 없으면 이 요청에서 만듭니다.\n\n" +
                "- Apple 은 `appAccountToken`, Google 은 `obfuscatedAccountId` 에 이 값을 넣습니다. 넣지 않은 " +
                "구매는 서버가 주인을 확인할 수 없어 지급하지 않습니다.\n" +
                "- 같은 회원에게는 항상 같은 값이고, 탈퇴 후 같은 소셜 계정으로 재가입해도 같습니다.\n" +
                "- 구매 직전마다 호출해도 안전합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |",
    )
    @GetMapping("/account-token")
    fun accountToken(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<PaymentAccountTokenResponse> =
        ApiResponse.success(PaymentAccountTokenResponse.from(paymentAccountService.accountTokenOf(principal.memberId)))
}
