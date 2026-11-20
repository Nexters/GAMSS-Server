package com.nexters.gamss.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "token_ledger")
@EntityListeners(AuditingEntityListener::class)
class TokenLedgerEntry(
    @Column(name = "subject_key", length = 64, nullable = false, updatable = false)
    val subjectKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false, updatable = false)
    val type: TokenLedgerType,
    @Column(name = "amount", nullable = false, updatable = false)
    val amount: Long,
    @Column(name = "balance_after", nullable = false, updatable = false)
    val balanceAfter: Long,
    @Column(name = "ref_id", length = LedgerRefId.MAX_LENGTH, updatable = false)
    val refId: String?,
    @Column(name = "admin_email", length = AdminNote.MAX_EMAIL_LENGTH, updatable = false)
    val adminEmail: String? = null,
    @Column(name = "reason", length = AdminNote.MAX_REASON_LENGTH, updatable = false)
    val reason: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    fun isReplayOf(change: TokenWalletChange): Boolean =
        subjectKey == change.subjectKey && type == change.type && amount == change.type.signed(change.amount)
}
