package com.nexters.gamss.admin.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 대시보드 집계의 KST 날짜 경계 계산을 한곳에 모은다(SRP: 타임존·날짜축 계산만 담당).
 *
 * 기준 날짜([today])는 호출 측이 요청당 한 번 [today] 로 캡처해 모든 함수에 넘긴다 — 각 함수가 개별적으로
 * 현재 날짜를 다시 읽으면 요청이 KST 자정 경계를 걸칠 때 KPI 범위와 일별 축의 기준일이 어긋날 수 있다.
 */
object KstDashboardDates {
    private val ZONE = ZoneId.of("Asia/Seoul")

    /** 요청 시작 시점의 KST 날짜. 서비스 진입점에서 한 번만 호출한다. */
    fun today(): LocalDate = LocalDate.now(ZONE)

    /** [date] 00:00(KST) */
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(ZONE).toInstant()

    /** [date] 다음 날 00:00(KST) — [start, end) 반열림 구간의 end 로 쓴다. */
    fun startOfNextDay(date: LocalDate): Instant = date.plusDays(1).atStartOfDay(ZONE).toInstant()

    /** [today] 로부터 최근 [days]일 구간의 시작(그 중 가장 오래된 날의 00:00 KST). */
    fun daysAgoStart(
        today: LocalDate,
        days: Int,
    ): Instant = today.minusDays((days - 1).toLong()).atStartOfDay(ZONE).toInstant()

    /** [today] 기준 최근 [days]일의 날짜 축(오래된 날 → 오늘). 빈 날도 0으로 채우기 위한 기준. */
    fun dateAxis(
        today: LocalDate,
        days: Int,
    ): List<LocalDate> = (0 until days).map { today.minusDays((days - 1 - it).toLong()) }

    /** [instant]가 속한 KST 달력 날짜. */
    fun dateOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()
}
