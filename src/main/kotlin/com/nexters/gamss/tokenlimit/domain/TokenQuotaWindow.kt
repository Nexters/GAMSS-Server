package com.nexters.gamss.tokenlimit.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 일일 한도가 세는 '하루'의 경계. 자정이 아니라 정책의 리셋 시각(KST [TokenPolicy.resetHour])이다.
 *
 * 적립과 판정이 같은 값을 써야 한다 - 각자 계산하면 경계 근처에서 방금 적립한 사용량이 다른
 * 구간으로 들어가 판정에서 빠진다.
 */
object TokenQuotaWindow {
    /**
     * 지금이 속한 구간의 시작. 아직 오늘 리셋 시각 전이면 어제 리셋 시각이 시작이다.
     *
     * 받은 시각을 먼저 KST 로 옮긴다. `toLocalDate()` 는 그 값이 들고 있는 존의 날짜를 주므로,
     * 다른 존의 시각을 그대로 쓰면 구간이 하루 밀린다.
     */
    fun startOf(
        resetHour: Int,
        now: ZonedDateTime = ZonedDateTime.now(ZONE),
    ): Instant {
        val seoulNow = now.withZoneSameInstant(ZONE)
        val todayReset = seoulNow.toLocalDate().atTime(resetHour, 0).atZone(ZONE)
        if (seoulNow < todayReset) {
            return todayReset.minusDays(1).toInstant()
        }
        return todayReset.toInstant()
    }

    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
}
