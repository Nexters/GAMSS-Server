package com.nexters.gamss.payment.domain

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

/**
 * 스토어 결제 한 건. `(platform, store_transaction_key)` 유니크가 같은 거래의 두 번째 지급을 막는다.
 *
 * 탈퇴 후에도 보존하므로(5년) 회원이 아니라 쿼터 주체([subjectKey])에 붙는다. [grantedTokens] 는
 * 지급 당시 값이라, 상품 지급량이 나중에 바뀌어도 회수량이 흔들리지 않는다.
 */
@Entity
@Table(name = "payment")
@EntityListeners(AuditingEntityListener::class)
class Payment(
    @Column(name = "subject_key", length = 64, nullable = false, updatable = false)
    val subjectKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 20, nullable = false, updatable = false)
    val platform: PaymentPlatform,
    @Column(name = "store_transaction_key", length = StoreTransactionKey.MAX_LENGTH, nullable = false, updatable = false)
    val storeTransactionKey: String,
    @Column(name = "store_purchase_token", columnDefinition = "TEXT", updatable = false)
    val storePurchaseToken: String?,
    @Column(name = "store_order_id", length = 128, updatable = false)
    val storeOrderId: String?,
    @Column(name = "product_id", length = 100, nullable = false, updatable = false)
    val productId: String,
    @Column(name = "quantity", nullable = false, updatable = false)
    val quantity: Int,
    @Column(name = "granted_tokens", nullable = false, updatable = false)
    val grantedTokens: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 20, nullable = false, updatable = false)
    val environment: PaymentEnvironment,
    @Column(name = "purchased_at", nullable = false, updatable = false)
    val purchasedAt: Instant,
) {
    init {
        require(quantity > 0) { "구매 수량은 양수여야 합니다: $quantity" }
        require(grantedTokens > 0) { "지급량은 양수여야 합니다: $grantedTokens" }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "refunded_tokens", nullable = false)
    var refundedTokens: Long = 0L
        protected set

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    /** 같은 거래가 다시 왔을 때 처음 지급과 같은 내용인지. 다르면 거래 키를 만드는 쪽의 버그다. */
    fun isSamePurchase(
        subjectKey: String,
        productId: String,
        quantity: Int,
    ): Boolean = this.subjectKey == subjectKey && this.productId == productId && this.quantity == quantity

    /**
     * 이 결제에서 지금까지 회수됐어야 할 총량을 [targetRefundedTokens] 로 맞추고, 바뀐 양을 돌려준다.
     *
     * 양수면 더 회수해야 하고 음수면 환불 취소로 되돌려줘야 한다. 같은 목표가 두 번 오면 두 번째는 0 이라
     * 아무것도 하지 않는다. 그래서 웹훅과 대사 배치가 같은 환불을 겹쳐 들고 와도 한 번만 반영된다.
     */
    fun refundTo(targetRefundedTokens: Long): Long {
        require(targetRefundedTokens in 0..grantedTokens) {
            "회수 목표는 0 이상 지급량($grantedTokens) 이하여야 합니다: $targetRefundedTokens"
        }
        val delta = targetRefundedTokens - refundedTokens
        refundedTokens = targetRefundedTokens
        return delta
    }

    fun purchaseRefId(): String = "$PURCHASE_REF_PREFIX:$platform:$storeTransactionKey"

    /** [sourceId] 는 환불을 알려 온 출처(웹훅 알림 ID 등)다. 같은 출처는 같은 변동으로 묶인다. */
    fun refundRefId(sourceId: String): String {
        require(sourceId.isNotBlank()) { "환불 출처는 비어 있을 수 없습니다." }
        return "$REFUND_REF_PREFIX:$platform:$storeTransactionKey:$sourceId"
    }

    private companion object {
        const val PURCHASE_REF_PREFIX = "purchase"
        const val REFUND_REF_PREFIX = "refund"
    }
}
