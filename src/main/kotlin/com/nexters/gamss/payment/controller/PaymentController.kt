package com.nexters.gamss.payment.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.payment.controller.dto.ApplePurchaseRequest
import com.nexters.gamss.payment.controller.dto.ApplePurchaseResponse
import com.nexters.gamss.payment.controller.dto.GooglePurchaseRequest
import com.nexters.gamss.payment.controller.dto.GooglePurchaseResponse
import com.nexters.gamss.payment.controller.dto.PaymentAccountTokenResponse
import com.nexters.gamss.payment.controller.dto.TokenProductResponse
import com.nexters.gamss.payment.service.ApplePurchaseService
import com.nexters.gamss.payment.service.GooglePurchaseService
import com.nexters.gamss.payment.service.PaymentAccountService
import com.nexters.gamss.payment.service.TokenProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "유료 토큰 인앱결제 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val tokenProductService: TokenProductService,
    private val paymentAccountService: PaymentAccountService,
    private val googlePurchaseService: GooglePurchaseService,
    private val applePurchaseService: ApplePurchaseService,
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

    @Operation(
        summary = "Google Play 구매 검증과 지급",
        description =
            "Google Play 에서 산 토큰 상품을 서버가 Google 에 직접 확인한 뒤 유료 토큰을 지급합니다.\n\n" +
                "- 구매할 때 `setObfuscatedAccountId` 에 계정 토큰(`GET /api/payments/account-token`)을 넣어야 합니다.\n" +
                "- 앱은 `consumeAsync`·`acknowledgePurchase` 를 부르지 않습니다. 서버가 지급한 뒤 consume 합니다.\n" +
                "- 같은 구매를 다시 보내도 추가로 지급하지 않고 같은 `grantedTokens` 로 성공합니다. `paidBalance` 는 " +
                "응답 시점의 잔액이라 처음 응답과 다를 수 있습니다.\n" +
                "- 결제가 아직 끝나지 않았으면 200 에 `status: PENDING` 으로 응답하고 지급하지 않습니다. 지급 완료로 " +
                "표시하지 말고, 결제가 끝난 뒤 다시 보냅니다.\n" +
                "- 앱을 켤 때(`onResume`) `queryPurchasesAsync` 로 남아 있는 구매를 이 API 로 다시 보냅니다. 403·409 는 " +
                "바로 다시 보내도 결과가 같으니 즉시 재시도하지 않습니다. 다만 로그인한 회원이나 서버의 상품 목록이 바뀌면 " +
                "지급될 수 있으므로, 다음 실행 때 다시 보내는 대상에서는 빼지 않습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | productId·purchaseToken 누락 또는 길이 초과 |\n" +
                "| PURCHASE_INVALID | 400 | Google 이 모르는 구매 토큰이거나 상태·상품 구성을 알 수 없는 구매 |\n" +
                "| PURCHASE_PRODUCT_MISMATCH | 400 | 구매한 상품이 요청한 productId 와 다름 |\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |\n" +
                "| PURCHASE_ACCESS_DENIED | 403 | 구매에 계정 토큰이 없거나 다른 회원의 계정 토큰이 담김 |\n" +
                "| PURCHASE_CANCELLED | 409 | 취소되었거나 환불된 구매 |\n" +
                "| PURCHASE_QUANTITY_UNSUPPORTED | 409 | 여러 개를 한 번에 산 구매(다중 수량 미지원) |\n" +
                "| PAYMENT_PRODUCT_UNAVAILABLE | 409 | 판매 중이 아닌 상품 |\n" +
                "| PAYMENT_STORE_UNAVAILABLE | 503 | Google 과 통신할 수 없음 — 잠시 후 다시 보냄 |",
    )
    @PostMapping("/google")
    fun purchaseGoogle(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: GooglePurchaseRequest,
    ): ApiResponse<GooglePurchaseResponse> {
        val result =
            googlePurchaseService.purchase(
                memberId = principal.memberId,
                productId = request.productId,
                purchaseToken = request.purchaseToken,
            )
        return ApiResponse.success(GooglePurchaseResponse.from(result))
    }

    @Operation(
        summary = "App Store 구매 검증과 지급",
        description =
            "StoreKit 2 거래의 Apple 서명을 서버가 확인한 뒤 유료 토큰을 지급합니다.\n\n" +
                "- 구매할 때 `.appAccountToken(UUID)` 옵션에 계정 토큰(`GET /api/payments/account-token`)을 넣어야 합니다.\n" +
                "- 지급량은 상품 지급량 x 거래 수량입니다.\n" +
                "- 같은 거래를 다시 보내도 추가로 지급하지 않고 같은 `grantedTokens` 로 성공합니다. `paidBalance` 는 " +
                "응답 시점의 잔액이라 처음 응답과 다를 수 있습니다.\n" +
                "- 앱을 켤 때 `Transaction.unfinished` 를 순회하며 이 API 로 다시 보냅니다.\n\n" +
                "**finish 규칙**\n\n" +
                "Apple 은 finish 하지 않은 거래를 자동 환불하지 않고 다음 실행에 다시 보여 줍니다. 남겨 두는 비용은 없고, " +
                "잘못 finish 하면 결제만 되고 토큰은 지급되지 않은 채 복구할 수 없습니다. 그래서 아래 표에서 '한다'인 " +
                "경우에만 `transaction.finish()` 를 부릅니다.\n\n" +
                "| 응답 | finish |\n" +
                "|---|---|\n" +
                "| 200 (재요청 포함) | 한다 |\n" +
                "| PURCHASE_INVALID | 한다 |\n" +
                "| PURCHASE_CANCELLED | 한다 |\n" +
                "| 그 밖의 모든 실패(403, 409 PAYMENT_PRODUCT_UNAVAILABLE, 401, 5xx, 네트워크 오류) | 하지 않는다. 다음 실행에 다시 보낸다 |\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | signedTransaction 누락 또는 길이 초과 |\n" +
                "| PURCHASE_INVALID | 400 | Apple 서명을 확인할 수 없거나, 다른 앱의 거래거나, 소모성 상품이 아니거나, 수량이 범위를 벗어남 |\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음 또는 무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료. 재발급 후 재시도 |\n" +
                "| WITHDRAWN_MEMBER | 403 | 탈퇴한 회원 |\n" +
                "| PURCHASE_ACCESS_DENIED | 403 | 거래에 계정 토큰이 없거나 다른 회원의 계정 토큰이 담김. 맞는 계정으로 로그인하면 지급될 수 있음 |\n" +
                "| PURCHASE_CANCELLED | 409 | 환불되었거나 취소된 거래 |\n" +
                "| PAYMENT_PRODUCT_UNAVAILABLE | 409 | 판매 중이 아닌 상품 |\n" +
                "| PAYMENT_STORE_UNAVAILABLE | 503 | 지금은 거래를 확인할 수 없음. 다음 실행에 다시 보냄 |",
    )
    @PostMapping("/apple")
    fun purchaseApple(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: ApplePurchaseRequest,
    ): ApiResponse<ApplePurchaseResponse> =
        ApiResponse.success(ApplePurchaseResponse.from(applePurchaseService.purchase(principal.memberId, request.signedTransaction)))
}
