package com.nexters.gamss.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 판매하는 토큰 상품. 지급량은 서버가 이 표로 정하고, 클라이언트가 보낸 수량·금액은 믿지 않는다.
 *
 * 상품은 마이그레이션으로만 넣고 바꾼다(V44). 그래서 이 엔티티로 고치는 경로가 없다.
 */
@Entity
@Table(name = "token_product")
class TokenProduct(
    /** 스토어 상품 ID. App Store 와 Google Play 에 같은 문자열로 등록한다. */
    @Id
    @Column(name = "product_id", length = 100, nullable = false, updatable = false)
    val productId: String,
    @Column(name = "token_amount", nullable = false, insertable = false, updatable = false)
    val tokenAmount: Long,
    @Column(name = "active", nullable = false, insertable = false, updatable = false)
    val active: Boolean,
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    val updatedAt: Instant,
) {
    /** 한 거래에 여러 개를 살 수 있다(Google 다중 수량). 넘치면 지급하지 않고 예외로 끊는다. */
    fun tokensFor(quantity: Int): Long {
        require(quantity > 0) { "구매 수량은 양수여야 합니다: $quantity" }
        return Math.multiplyExact(tokenAmount, quantity.toLong())
    }
}
