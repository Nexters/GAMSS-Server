package com.nexters.gamss.wallet.repository

import com.nexters.gamss.wallet.domain.TokenLedgerEntry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface TokenLedgerRepository : JpaRepository<TokenLedgerEntry, Long> {
    fun findByRefId(refId: String): TokenLedgerEntry?

    fun existsByRefId(refId: String): Boolean

    fun findBySubjectKey(
        subjectKey: String,
        pageable: Pageable,
    ): Page<TokenLedgerEntry>
}
