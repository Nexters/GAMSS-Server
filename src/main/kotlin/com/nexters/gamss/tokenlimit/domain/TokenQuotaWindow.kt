package com.nexters.gamss.tokenlimit.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 일일 토큰 한도가 세는 '하루'의 경계.
 *
 * 자정이 아니라 정책의 리셋 시각(KST [TokenPolicy.resetHour])이 경계다. 예를 들어 resetHour=5 면
 * 오늘 05:00 ~ 내일 05:00 이 한 구간이고, 그 구간의 소비 합이 상한에 닿으면 생성을 막는다.
 *
 * **적립과 판정이 같은 값을 써야 한다.** 두 경로가 각자 계산하면 경계 근처에서 방금 적립한 사용량이
 * 다른 구간으로 들어가 판정에서 빠진다. 그래서 계산을 한 자리에 두고 양쪽이 이것만 부른다
 * ([com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder],
 * [com.nexters.gamss.tokenlimit.service.DailyTokenLimitService]).
 *
 * 시각을 인자로 받는 것은 테스트가 경계를 직접 넘겨보기 위해서다.
 */
object TokenQuotaWindow {
    /**
     * 지금이 속한 구간의 시작. 아직 오늘 리셋 시각 전이면 어제 리셋 시각이 시작이다.
     *
     * 받은 시각을 먼저 KST 로 옮긴다. `toLocalDate()` 는 **그 값이 들고 있는 존**의 날짜를 주므로,
     * 다른 존의 시각을 그대로 쓰면 날짜가 어긋나 구간이 하루 밀린다(KST 06:00 은 UTC 로 전날 21:00 이라
     * 어제 날짜가 나온다).
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
