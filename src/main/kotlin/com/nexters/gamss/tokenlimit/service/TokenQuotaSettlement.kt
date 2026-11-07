package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.PaidTokenShare
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.service.TokenWalletService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 일일 사용량 적립과 유료 토큰 차감의 트랜잭션 작업 단위(#239). 재시도와 실패 처리는 트랜잭션 밖에서
 * 해야 하므로 여기서 하지 않는다. 바깥 진입점은 [TokenQuotaRecorder] 다.
 *
 * 잠금은 사용량 행을 먼저, 지갑 행을 나중에 잡는다(#238). 두 행을 함께 잠그는 경로는 여기뿐이라 순서가 뒤집히지 않는다.
 */
@Service
class TokenQuotaSettlement(
    private val usageRepository: TokenQuotaUsageRepository,
    private val walletService: TokenWalletService,
) {
    /**
     * 적립 upsert 가 사용량 행의 배타 잠금을 잡으므로 같은 주체의 다음 생성은 여기서 기다린다. 그래서
     * 동시 생성 두 건이 같은 사용량을 보고 둘 다 한도 안이라고 판단하는 일이 없다.
     *
     * 지갑이 없거나 잔액이 0 이하면 [TokenWalletService] 가 아무것도 깎지 않고, 잔액보다 많이 쓰면 잔액까지만
     * 깎는다. 원장에는 실제로 깎은 양만 남는다.
     *
     * @return 주체 매핑이 없어 적립하지 못했으면 false
     */
    @Transactional
    fun settle(
        memberId: Long,
        subjectKey: String,
        windowStart: Instant,
        limit: Long,
        delta: Long,
    ): Boolean {
        if (usageRepository.addUsedTokens(memberId, windowStart, delta) == 0) {
            return false
        }
        val newUsed =
            usageRepository.findUsedTokensForUpdate(subjectKey, windowStart)
                ?: error("방금 적립한 사용량 행이 없습니다: memberId=$memberId")
        val paidShare = PaidTokenShare.of(newUsed, delta, limit)
        if (paidShare > 0) {
            walletService.apply(TokenWalletChange(subjectKey, TokenLedgerType.CONSUME, TokenAmount(paidShare), null))
        }
        return true
    }
}
