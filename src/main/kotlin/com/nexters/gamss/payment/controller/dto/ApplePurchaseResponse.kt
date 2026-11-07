package com.nexters.gamss.payment.controller.dto

import com.nexters.gamss.payment.service.ApplePurchaseResult
import io.swagger.v3.oas.annotations.media.Schema

data class ApplePurchaseResponse(
    @field:Schema(description = "구매한 상품 ID", example = "token_300000")
    val productId: String,
    @field:Schema(description = "이 거래로 지급된 유료 토큰 수(상품 지급량 x 수량)", example = "300000")
    val grantedTokens: Long,
    @field:Schema(description = "응답 시점의 유료 토큰 잔액", example = "300000")
    val paidBalance: Long,
) {
    companion object {
        fun from(result: ApplePurchaseResult): ApplePurchaseResponse =
            ApplePurchaseResponse(
                productId = result.productId,
                grantedTokens = result.grantedTokens,
                paidBalance = result.paidBalance,
            )
    }
}
