package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `하한은 설정한 날짜의 05시다`() {
        assertEquals(Instant.parse("2026-08-14T20:00:00Z"), window.createdAfter())
    }

    private fun kst(localDateTime: String): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.parse(localDateTime), ZoneId.of(AutoCardWindow.ZONE_ID))
}
