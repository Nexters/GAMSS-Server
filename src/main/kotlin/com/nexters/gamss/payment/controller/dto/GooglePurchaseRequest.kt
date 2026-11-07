package com.nexters.gamss.payment.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GooglePurchaseRequest(
    @field:NotBlank(message = "productId는 필수입니다.")
    @field:Size(max = 100, message = "productId는 100자 이하여야 합니다.")
    @field:Schema(description = "구매한 상품 ID(Purchase.getProducts() 의 값)", example = "token_300000")
    val productId: String,
    // Google 은 purchaseToken 길이를 문서화하지 않는다. 실제 값은 수백 자라 넉넉히 두되, 상한이 없으면
    // 거대한 문자열이 그대로 Google 요청 경로에 실린다.
    @field:NotBlank(message = "purchaseToken은 필수입니다.")
    @field:Size(max = 4096, message = "purchaseToken은 4096자 이하여야 합니다.")
    @field:Schema(description = "Google Play 구매 토큰(Purchase.getPurchaseToken())")
    val purchaseToken: String,
)
