package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 두 배치(05:00 자동 종료, 04:30 리마인더)가 같은 대상을 보게 하는 기준이라, 경계 판정이 이 기능의
 * 핵심이다. 어긋나면 닫히지도 않을 방을 두고 알리거나, 알림 없이 닫히는 방이 생긴다.
 */
class AutoCardWindowTest {
    private val window = AutoCardWindow(CardProperties(autoCardStartDate = LocalDate.of(2026, 8, 15)))

    /** 04:30 리마인더가 도는 시각. 아직 오늘 경계 전이라 어제 05:00 이 상한이어야 한다. */
    @Test
    fun `04시 30분에는 어제 05시가 상한이다`() {
        assertEquals(
            Instant.parse("2026-08-17T20:00:00Z"),
            window.createdBefore(kst("2026-08-19T04:30:00")),
        )
    }

    /** 30분 뒤 5시 배치. 상한이 오늘 05:00 으로 넘어가, 그 사이에 만들어진 방까지 대상이 된다. */
    @Test
    fun `05시 정각에는 오늘 05시가 상한이다`() {
        assertEquals(
            Instant.parse("2026-08-18T20:00:00Z"),
            window.createdBefore(kst("2026-08-19T05:00:00")),
        )
    }

    /** 경계 직전 1초. 여기서 오늘로 넘어가버리면 하루가 통째로 겹친다. */
    @Test
    fun `경계 1초 전까지는 어제가 상한이다`() {
        assertEquals(
            Instant.parse("2026-08-17T20:00:00Z"),
            window.createdBefore(kst("2026-08-19T04:59:59")),
        )
    }

    /** 자정을 넘겨도 05시 전이면 여전히 어제 구간이다 — 새벽까지 이어 쓴 기록은 그 전날에 속한다. */
    @Test
    fun `자정 직후도 어제 구간이다`() {
        assertEquals(
            Instant.parse("2026-08-17T20:00:00Z"),
            window.createdBefore(kst("2026-08-19T00:10:00")),
        )
    }

    /**
     * 이 기능의 핵심 관계다. 04:30 리마인더는 **30분 뒤 배치가 닫을 방**을 알려야 하므로, 두 시점이
     * 같은 상한을 봐야 한다. 어긋나면 어젯밤에 쓰다 만 방이 알림 없이 닫힌다.
     */
    @Test
    fun `04시 30분 리마인더와 30분 뒤 배치는 같은 상한을 본다`() {
        assertEquals(
            window.createdBefore(kst("2026-08-19T05:00:00")),
            window.createdBeforeOfNextRun(kst("2026-08-19T04:30:00")),
        )
    }

    /** 가장 흔한 경우 — 어젯밤에 쓰다 만 방은 리마인더 대상에 들어와야 한다. */
    @Test
    fun `어젯밤에 만든 방도 리마인더 대상 기간에 들어온다`() {
        val lastNight = Instant.parse("2026-08-18T13:00:00Z") // KST 08-18 22:00

        val upperBound = window.createdBeforeOfNextRun(kst("2026-08-19T04:30:00"))

        assertTrue(lastNight < upperBound, "30분 뒤 닫힐 방이 리마인더 기간에 들어와야 한다")
    }

    @Test
    fun `하한은 설정한 날짜의 05시다`() {
        assertEquals(Instant.parse("2026-08-14T20:00:00Z"), window.createdAfter())
    }

    private fun kst(localDateTime: String): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.parse(localDateTime), ZoneId.of(AutoCardWindow.ZONE_ID))
}
