package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.AdminPaidTokens
import com.nexters.gamss.global.response.PageResponse
import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class PaidTokensResponse(
    @field:Schema(description = "현재 유료 토큰 잔액. 회수로 음수일 수 있다.", example = "300000")
    val balance: Long,
    @field:Schema(description = "원장(최신순)")
    val ledger: PageResponse<TokenLedgerResponse>,
) {
    companion object {
        fun from(paidTokens: AdminPaidTokens): PaidTokensResponse =
            PaidTokensResponse(paidTokens.balance, PageResponse.from(paidTokens.ledger, TokenLedgerResponse::from))
    }
}

data class TokenLedgerResponse(
    @field:Schema(description = "원장 ID", example = "1")
    val id: Long,
    @field:Schema(
        description = "변동 타입",
        example = "ADMIN_GRANT",
        allowableValues = ["PURCHASE", "CONSUME", "REFUND", "REFUND_REVERSED", "ADMIN_GRANT", "ADMIN_REVOKE"],
    )
    val type: String,
    @field:Schema(description = "증감량(차감은 음수)", example = "300000")
    val amount: Long,
    @field:Schema(description = "반영 직후 잔액", example = "300000")
    val balanceAfter: Long,
    @field:Schema(description = "처리한 관리자 이메일(관리자 변동이 아니면 null)", example = "admin@gamss.kr")
    val adminEmail: String?,
    @field:Schema(description = "처리 사유(관리자 변동이 아니면 null)", example = "결제 후 미지급 보상")
    val reason: String?,
    @field:Schema(description = "반영 일시")
    val createdAt: Instant,
) {
    companion object {
        fun from(entry: TokenLedgerEntry): TokenLedgerResponse =
            TokenLedgerResponse(
                id = entry.id,
                type = entry.type.name,
                amount = entry.amount,
                balanceAfter = entry.balanceAfter,
                adminEmail = entry.adminEmail,
                reason = entry.reason,
                createdAt = entry.createdAt,
            )
    }
}
