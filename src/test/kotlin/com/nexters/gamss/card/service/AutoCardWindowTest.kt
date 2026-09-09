package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * 리마인더 시각이 하루 경계에서 만들어져 나오는지 본다. 값을 그대로 적지 않고 관계로 적은 것은,
     * 경계를 옮겼을 때 이 관계가 그대로여야 리마인더 cron 과 백오피스 표의 틈 판정이 함께 따라오기
     * 때문이다. 어긋나면 배치가 닫은 뒤에 알리거나, 표가 실제와 다른 틈을 본다.
     */
    @Test
    fun `리마인더 시각은 하루 경계에서 앞당긴 만큼이다`() {
        assertEquals(
            AutoCardWindow.DAY_BOUNDARY_HOUR * 60 - AutoCardWindow.REMINDER_MINUTES_BEFORE,
            AutoCardWindow.REMINDER_HOUR * 60 + AutoCardWindow.REMINDER_MINUTE,
        )
    }

    /** 틈 안. 이 방은 리마인더가 볼 수 없었는데도 30분 뒤 배치가 곧바로 종료시킨다. */
    @Test
    fun `04시 30분과 05시 사이에 만든 방은 리마인더 틈에 걸린다`() {
        assertTrue(window.isCreatedInReminderGap(kstInstant("2026-08-19T04:40:00")))
    }

    /** 시작 경계는 포함이다. 04:30 정각 생성은 리마인더 조회가 잡지 못한다. */
    @Test
    fun `04시 30분 정각도 리마인더 틈에 걸린다`() {
        assertTrue(window.isCreatedInReminderGap(kstInstant("2026-08-19T04:30:00")))
    }

    /** 끝 경계는 제외다. 05:00 생성은 이번 배치가 아니라 다음 회차 몫이라 틈이 아니다. */
    @Test
    fun `05시 정각은 리마인더 틈이 아니다`() {
        assertFalse(window.isCreatedInReminderGap(kstInstant("2026-08-19T05:00:00")))
    }

    /** 틈 1분 전. 이 방은 리마인더 대상이었으므로 기록이 없으면 다른 이유를 봐야 한다. */
    @Test
    fun `04시 29분은 리마인더 틈이 아니다`() {
        assertFalse(window.isCreatedInReminderGap(kstInstant("2026-08-19T04:29:00")))
    }

    /** UTC 로 읽으면 한국 시간 04:40 이 전날 19:40 이라 틈을 놓친다. */
    @Test
    fun `틈 판정은 한국 시간 기준이다`() {
        assertTrue(window.isCreatedInReminderGap(Instant.parse("2026-08-18T19:40:00Z")))
        assertFalse(window.isCreatedInReminderGap(Instant.parse("2026-08-19T04:40:00Z")))
    }

    @Test
    fun `하한은 설정한 날짜의 05시다`() {
        assertEquals(Instant.parse("2026-08-14T20:00:00Z"), window.createdAfter())
    }

    private fun kst(localDateTime: String): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.parse(localDateTime), ZoneId.of(AutoCardWindow.ZONE_ID))

    private fun kstInstant(localDateTime: String): Instant = kst(localDateTime).toInstant()
}
