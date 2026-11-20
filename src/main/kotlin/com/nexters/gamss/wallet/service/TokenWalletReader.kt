package com.nexters.gamss.wallet.service

import com.nexters.gamss.wallet.domain.LedgerRefId
import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class TokenWalletReader(
    private val walletRepository: TokenWalletRepository,
    private val ledgerRepository: TokenLedgerRepository,
) {
    fun balanceOf(memberId: Long): Long = walletRepository.findBalance(memberId)

    /** 이 멱등 키로 반영된 변동이 이미 있는지. 같은 원인이 다시 왔을 때 부르는 쪽 상태까지 건드리지 않게 먼저 묻는다. */
    fun isApplied(refId: LedgerRefId): Boolean = ledgerRepository.existsByRefId(refId.value)

    fun findApplied(refId: LedgerRefId): TokenLedgerEntry? = ledgerRepository.findByRefId(refId.value)

    /** 최신순. 같은 주체의 기록은 id 순서가 반영 순서다(V43). */
    fun ledgerOf(
        subjectKey: String,
        page: Int,
        size: Int,
    ): Page<TokenLedgerEntry> =
        ledgerRepository.findBySubjectKey(subjectKey, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
}
