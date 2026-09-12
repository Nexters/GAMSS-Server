package com.nexters.gamss.tokenlimit.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 적립과 판정이 같은 구간을 봐야 한다. 경계가 어긋나면 방금 적립한 사용량이 다른 구간으로 들어가
 * 판정에서 빠지고, 리셋 시각 근처에서 한도가 두 번 리셋되는 것처럼 보인다.
 */
class TokenQuotaWindowTest {
    @Test
    fun `리셋 시각 정각이면 그 시각이 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-12T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T05:00:00")))
    }

    /** 1초 전까지는 어제 구간이다. 여기서 오늘로 넘어가면 하루가 통째로 겹친다. */
    @Test
    fun `리셋 시각 1초 전이면 어제가 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-11T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T04:59:59")))
    }

    /** 자정을 넘겨도 리셋 시각 전이면 여전히 어제 구간이다. */
    @Test
    fun `자정 직후도 어제 구간이다`() {
        assertEquals(kstInstant("2026-09-11T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T00:10:00")))
    }

    /** resetHour 는 백오피스에서 바뀌는 값이라 0 도 들어올 수 있다. 그때는 자정이 경계다. */
    @Test
    fun `리셋 시각이 0시면 자정이 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-12T00:00:00"), TokenQuotaWindow.startOf(0, kst("2026-09-12T00:00:00")))
        assertEquals(kstInstant("2026-09-11T00:00:00"), TokenQuotaWindow.startOf(0, kst("2026-09-11T23:59:59")))
    }

    /**
     * 다른 존의 시각이 들어와도 구간은 한국 시간으로 계산해야 한다.
     *
     * KST 06:00 은 UTC 로 전날 21:00 이다. 받은 값을 KST 로 옮기지 않고 `toLocalDate()` 를 쓰면 전날
     * 날짜가 나와 구간이 하루 밀린다 - 그러면 이미 지난 구간에 적립하고 판정도 그쪽을 본다.
     */
    @Test
    fun `구간은 한국 시간 기준이다`() {
        val kstSixAmAsUtc = ZonedDateTime.ofInstant(kstInstant("2026-09-12T06:00:00"), ZoneId.of("UTC"))

        assertEquals(kstInstant("2026-09-12T05:00:00"), TokenQuotaWindow.startOf(5, kstSixAmAsUtc))
    }

    private fun kst(localDateTime: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(localDateTime), ZONE)

    private fun kstInstant(localDateTime: String): Instant = kst(localDateTime).toInstant()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
