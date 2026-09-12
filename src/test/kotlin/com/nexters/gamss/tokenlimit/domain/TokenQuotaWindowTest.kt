package com.nexters.gamss.tokenlimit.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** 적립과 판정이 같은 구간을 봐야 한다. 어긋나면 방금 적립한 값이 판정에서 빠진다. */
class TokenQuotaWindowTest {
    @Test
    fun `리셋 시각 정각이면 그 시각이 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-12T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T05:00:00")))
    }

    @Test
    fun `리셋 시각 1초 전이면 어제가 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-11T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T04:59:59")))
    }

    @Test
    fun `자정 직후도 어제 구간이다`() {
        assertEquals(kstInstant("2026-09-11T05:00:00"), TokenQuotaWindow.startOf(5, kst("2026-09-12T00:10:00")))
    }

    /** resetHour 는 백오피스에서 바뀌는 값이라 0 도 들어올 수 있다. */
    @Test
    fun `리셋 시각이 0시면 자정이 구간 시작이다`() {
        assertEquals(kstInstant("2026-09-12T00:00:00"), TokenQuotaWindow.startOf(0, kst("2026-09-12T00:00:00")))
        assertEquals(kstInstant("2026-09-11T00:00:00"), TokenQuotaWindow.startOf(0, kst("2026-09-11T23:59:59")))
    }

    /** KST 06:00 은 UTC 로 전날 21:00 이다. 받은 값을 옮기지 않으면 구간이 하루 밀린다. */
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
