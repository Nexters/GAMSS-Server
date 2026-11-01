package com.nexters.gamss.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "token_wallet")
@EntityListeners(AuditingEntityListener::class)
class TokenWallet(
    @Id
    @Column(name = "subject_key", length = 64, nullable = false)
    val subjectKey: String,
    balance: Long = 0,
) {
    @Column(name = "balance", nullable = false)
    var balance: Long = balance
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    /** 반영할 양이 없으면 null 이다. 잔액이 0 이하일 때의 사용이 그렇다. */
    fun apply(change: TokenWalletChange): TokenLedgerEntry? {
        require(change.subjectKey == subjectKey) { "다른 주체의 지갑에 반영할 수 없습니다." }
        val delta = deltaOf(change)
        if (delta == 0L) {
            return null
        }
        balance = Math.addExact(balance, delta)
        return TokenLedgerEntry(subjectKey, change.type, delta, balance, change.refId?.value)
    }

    private fun deltaOf(change: TokenWalletChange): Long {
        val delta = change.type.signed(change.amount)
        if (!change.type.stopsAtZero) {
            return delta
        }
        return maxOf(delta, -maxOf(balance, 0))
    }
}
