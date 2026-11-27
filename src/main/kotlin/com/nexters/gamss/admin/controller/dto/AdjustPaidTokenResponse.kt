package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.wallet.service.TokenWalletResult
import io.swagger.v3.oas.annotations.media.Schema

data class AdjustPaidTokenResponse(
    @field:Schema(description = "반영된 증감량(회수는 음수)", example = "300000")
    val appliedAmount: Long,
    @field:Schema(description = "반영 후 잔액", example = "300000")
    val balance: Long,
    @field:Schema(description = "같은 멱등 키의 재요청이라 새로 반영하지 않았는지", example = "false")
    val replayed: Boolean,
) {
    companion object {
        fun from(result: TokenWalletResult): AdjustPaidTokenResponse =
            AdjustPaidTokenResponse(result.appliedAmount, result.balance, result.replayed)
    }
}
