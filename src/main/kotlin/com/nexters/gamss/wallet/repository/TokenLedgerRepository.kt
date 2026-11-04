package com.nexters.gamss.wallet.repository

import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import org.springframework.data.jpa.repository.JpaRepository

interface TokenLedgerRepository : JpaRepository<TokenLedgerEntry, Long> {
    fun findByRefId(refId: String): TokenLedgerEntry?
}
