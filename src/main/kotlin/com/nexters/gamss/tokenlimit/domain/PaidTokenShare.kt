package com.nexters.gamss.tokenlimit.domain

/**
 * 생성 1건의 사용량 중 일일 한도를 넘겨 유료 토큰으로 내야 하는 몫(#239).
 *
 * 적립 전후에 한도를 넘긴 양의 차이로 구한다. 아직 안 넘긴 경우, 이번에 경계를 걸친 경우, 이미 넘긴
 * 경우가 한 식으로 처리되고, 정책이 하루 중간에 바뀌어도 그 뒤 생성부터 새 한도로 계산된다.
 */
object PaidTokenShare {
    /** [newUsed] 는 [delta] 를 적립한 뒤의 구간 사용량이다. */
    fun of(
        newUsed: Long,
        delta: Long,
        limit: Long,
    ): Long = overLimit(newUsed, limit) - overLimit(newUsed - delta, limit)

    private fun overLimit(
        used: Long,
        limit: Long,
    ): Long = maxOf(0, used - limit)
}
