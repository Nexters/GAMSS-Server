package com.nexters.gamss.admin.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.member.service.MemberSocialIdentityService
import com.nexters.gamss.wallet.domain.AdminNote
import com.nexters.gamss.wallet.domain.LedgerRefId
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.service.TokenWalletReader
import com.nexters.gamss.wallet.service.TokenWalletResult
import com.nexters.gamss.wallet.service.TokenWalletService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 백오피스의 유료 토큰 조회와 수동 지급·회수(#241).
 *
 * 경합 재시도가 트랜잭션 밖에서 돌아야 하므로 이 클래스는 트랜잭션을 열지 않는다.
 */
@Service
class AdminPaidTokenService(
    private val memberService: MemberService,
    private val identityService: MemberSocialIdentityService,
    private val walletReader: TokenWalletReader,
    private val walletService: TokenWalletService,
    private val conflictRetry: ConflictRetry,
) {
    fun paidTokensOf(
        memberId: Long,
        page: Int,
        size: Int,
    ): AdminPaidTokens {
        memberService.getById(memberId)
        val subjectKey =
            identityService.subjectKeyOf(memberId)
                ?: return AdminPaidTokens(balance = 0, ledger = Page.empty(PageRequest.of(page, size)))
        return AdminPaidTokens(walletReader.balanceOf(memberId), walletReader.ledgerOf(subjectKey, page, size))
    }

    fun grant(
        memberId: Long,
        amount: Long,
        idempotencyKey: UUID,
        note: AdminNote,
    ): TokenWalletResult = adjust(memberId, TokenLedgerType.ADMIN_GRANT, amount, idempotencyKey, note)

    fun revoke(
        memberId: Long,
        amount: Long,
        idempotencyKey: UUID,
        note: AdminNote,
    ): TokenWalletResult = adjust(memberId, TokenLedgerType.ADMIN_REVOKE, amount, idempotencyKey, note)

    private fun adjust(
        memberId: Long,
        type: TokenLedgerType,
        amount: Long,
        idempotencyKey: UUID,
        note: AdminNote,
    ): TokenWalletResult {
        memberService.getById(memberId)
        val subjectKey = identityService.subjectKeyOf(memberId) ?: throw BusinessException(ErrorCode.PAID_TOKEN_SUBJECT_NOT_FOUND)
        val change = TokenWalletChange(subjectKey, type, TokenAmount(amount), LedgerRefId("$REF_PREFIX$idempotencyKey"), note)
        // 지갑 서비스는 같은 키의 다른 변경을 서버 오류로 거부하므로, 화면 실수로 볼 수 있게 먼저 걸러 낸다.
        val existing = walletReader.findApplied(checkNotNull(change.refId))
        if (existing != null && !existing.isReplayOf(change)) {
            throw BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
        }
        return conflictRetry.execute { walletService.apply(change) }
    }

    private companion object {
        const val REF_PREFIX = "admin:"
    }
}

data class AdminPaidTokens(
    val balance: Long,
    val ledger: Page<TokenLedgerEntry>,
)
