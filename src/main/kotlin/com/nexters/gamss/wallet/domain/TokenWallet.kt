package com.nexters.gamss.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 잔액은 [com.nexters.gamss.wallet.service.TokenWalletService] 가 쿼리로만 바꾼다. 이 엔티티로 고치지 않는다. */
@Entity
@Table(name = "token_wallet")
class TokenWallet(
    @Id
    @Column(name = "subject_key", length = 64, nullable = false, updatable = false)
    val subjectKey: String,
    @Column(name = "balance", nullable = false, insertable = false, updatable = false)
    val balance: Long,
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    val updatedAt: Instant,
)
