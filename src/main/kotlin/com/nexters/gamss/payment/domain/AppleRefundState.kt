package com.nexters.gamss.payment.domain

/**
 * App Store 거래 하나의 환불 상태. 알림 종류(환불, 환불 취소)를 명령으로 받지 않고 이 상태로 저장한 뒤, 회수 목표를
 * 상태에서 계산한다. 그래야 알림이 늦거나 순서가 바뀌어 와도 결과가 같다.
 *
 * [revocationPercentage] 는 Apple 이 주는 부분 환불 비율로 단위가 milliunits 다([FULL] 이 100%). 없으면 전액 환불이다.
 */
data class AppleRefundState(
    val refunded: Boolean,
    val revocationPercentage: Int?,
) {
    init {
        require(revocationPercentage == null || revocationPercentage in 0..FULL) {
            "부분 환불 비율은 0 이상 $FULL 이하여야 합니다: $revocationPercentage"
        }
        require(refunded || revocationPercentage == null) { "환불되지 않은 거래에 환불 비율이 있습니다: $revocationPercentage" }
    }

    /** 이 상태에서 회수됐어야 할 총량. 부분 환불은 사용자에게 유리하게 내림한다. */
    fun targetFor(grantedTokens: Long): Long {
        if (!refunded) {
            return 0
        }
        val percentage = revocationPercentage ?: return grantedTokens
        return grantedTokens * percentage / FULL
    }

    companion object {
        const val FULL = 100_000

        val NOT_REFUNDED = AppleRefundState(refunded = false, revocationPercentage = null)

        fun refunded(revocationPercentage: Int?) = AppleRefundState(refunded = true, revocationPercentage = revocationPercentage)
    }
}
