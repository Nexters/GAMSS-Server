package com.nexters.gamss.payment.controller.dto

import com.nexters.gamss.payment.domain.PaymentAccountToken
import io.swagger.v3.oas.annotations.media.Schema

data class PaymentAccountTokenResponse(
    @field:Schema(
        description = "구매 계정 토큰(UUID). Apple appAccountToken, Google obfuscatedAccountId 에 넣습니다",
        example = "3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c",
    )
    val accountToken: String,
) {
    companion object {
        fun from(token: PaymentAccountToken): PaymentAccountTokenResponse = PaymentAccountTokenResponse(accountToken = token.value)
    }
}
