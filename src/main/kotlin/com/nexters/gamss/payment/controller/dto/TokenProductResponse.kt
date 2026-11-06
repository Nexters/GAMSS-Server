package com.nexters.gamss.payment.controller.dto

import com.nexters.gamss.payment.domain.TokenProduct
import io.swagger.v3.oas.annotations.media.Schema

data class TokenProductResponse(
    @field:Schema(description = "스토어 상품 ID. App Store·Google Play 에서 같은 값입니다", example = "token_300000")
    val productId: String,
    @field:Schema(description = "구매하면 지급되는 유료 토큰 수", example = "300000")
    val tokenAmount: Long,
) {
    companion object {
        fun from(product: TokenProduct): TokenProductResponse =
            TokenProductResponse(
                productId = product.productId,
                tokenAmount = product.tokenAmount,
            )
    }
}
