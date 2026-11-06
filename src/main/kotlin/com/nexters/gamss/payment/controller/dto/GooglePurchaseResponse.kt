package com.nexters.gamss.payment.controller.dto

import com.nexters.gamss.payment.service.GooglePurchaseResult
import io.swagger.v3.oas.annotations.media.Schema

data class GooglePurchaseResponse(
    @field:Schema(description = "GRANTED: 지급 완료(이미 지급된 구매 포함). PENDING: 결제 대기 중이라 지급하지 않음")
    val status: GooglePurchaseStatus,
    @field:Schema(description = "구매한 상품 ID", example = "token_300000")
    val productId: String,
    @field:Schema(description = "이 구매로 지급된 유료 토큰 수. PENDING 이면 null", example = "300000", nullable = true)
    val grantedTokens: Long?,
    @field:Schema(description = "응답 시점의 유료 토큰 잔액. PENDING 이면 null", example = "300000", nullable = true)
    val paidBalance: Long?,
) {
    companion object {
        fun from(result: GooglePurchaseResult): GooglePurchaseResponse =
            when (result) {
                is GooglePurchaseResult.Granted -> {
                    GooglePurchaseResponse(
                        status = GooglePurchaseStatus.GRANTED,
                        productId = result.productId,
                        grantedTokens = result.grantedTokens,
                        paidBalance = result.paidBalance,
                    )
                }

                is GooglePurchaseResult.Pending -> {
                    GooglePurchaseResponse(
                        status = GooglePurchaseStatus.PENDING,
                        productId = result.productId,
                        grantedTokens = null,
                        paidBalance = null,
                    )
                }
            }
    }
}

enum class GooglePurchaseStatus {
    GRANTED,
    PENDING,
}
