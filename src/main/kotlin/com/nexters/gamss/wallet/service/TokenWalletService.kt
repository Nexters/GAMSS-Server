package com.nexters.gamss.wallet.service

import com.nexters.gamss.wallet.domain.PaidTokenBalance
import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유료 토큰 잔액을 바꾸는 유일한 경로. 지갑 행을 잠그고 바꾼 뒤 원장에 남긴다.
 *
 * 부르는 쪽 트랜잭션에 참여한다. 그래서 [TokenLedgerConflictException] 의 재시도는
 * 가장 바깥 진입점이 [com.nexters.gamss.global.retry.ConflictRetry] 로 감싸야 한다.
 */
@Service
class TokenWalletService(
    private val walletRepository: TokenWalletRepository,
    private val ledgerRepository: TokenLedgerRepository,
) {
    @Transactional
    fun apply(change: TokenWalletChange): TokenWalletResult {
        try {
            return applyLocked(change)
        } catch (e: DataIntegrityViolationException) {
            throw TokenLedgerConflictException(change.refId, e)
        } catch (e: ConcurrencyFailureException) {
            throw TokenLedgerConflictException(change.refId, e)
        }
    }

    private fun applyLocked(change: TokenWalletChange): TokenWalletResult {
        val balance = lockedBalance(change) ?: return TokenWalletResult(appliedAmount = 0, balance = 0, replayed = false)
        val existing = change.refId?.let { ledgerRepository.findByRefId(it.value) }
        if (existing != null) {
            return replay(existing, change, balance)
        }
        val entry = balance.apply(change) ?: return TokenWalletResult(appliedAmount = 0, balance = balance.value, replayed = false)
        walletRepository.addBalance(change.subjectKey, entry.amount)
        ledgerRepository.saveAndFlush(entry)
        return TokenWalletResult(appliedAmount = entry.amount, balance = entry.balanceAfter, replayed = false)
    }

    // 0에서 멈추는 사용은 지갑이 없으면 깎을 것도 없으므로 빈 지갑을 만들지 않는다. 없는 행을
    // FOR UPDATE 로 읽으면 gap lock 이 걸려 다른 주체의 첫 지갑 생성까지 막으므로 먼저 확인만 한다.
    private fun lockedBalance(change: TokenWalletChange): PaidTokenBalance? {
        if (change.type.stopsAtZero && !walletRepository.existsById(change.subjectKey)) {
            return null
        }
        if (!change.type.stopsAtZero) {
            walletRepository.createIfAbsent(change.subjectKey)
        }
        val value = walletRepository.findBalanceForUpdate(change.subjectKey) ?: return null
        return PaidTokenBalance(change.subjectKey, value)
    }

    private fun replay(
        existing: TokenLedgerEntry,
        change: TokenWalletChange,
        balance: PaidTokenBalance,
    ): TokenWalletResult {
        check(existing.isReplayOf(change)) { "같은 멱등 키로 다른 변경이 들어왔습니다: refId=${change.refId}" }
        return TokenWalletResult(appliedAmount = existing.amount, balance = balance.value, replayed = true)
    }
}
