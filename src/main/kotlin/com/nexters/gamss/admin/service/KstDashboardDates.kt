package com.nexters.gamss.admin.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 대시보드 집계의 KST 날짜 경계 계산을 한곳에 모은다(SRP: 타임존·날짜축 계산만 담당).
 * 사용량·품질 집계 서비스가 공유해 '오늘'과 '일별 축'의 정의를 일관되게 유지한다.
 */
object KstDashboardDates {
    private val ZONE = ZoneId.of("Asia/Seoul")

    fun today(): LocalDate = LocalDate.now(ZONE)

    /** 오늘 00:00(KST) */
    fun startOfToday(): Instant = today().atStartOfDay(ZONE).toInstant()

    /** 내일 00:00(KST) — [start, end) 반열림 구간의 end 로 쓴다. */
    fun startOfTomorrow(): Instant = today().plusDays(1).atStartOfDay(ZONE).toInstant()

    /** 최근 [days]일 구간의 시작(그 중 가장 오래된 날의 00:00 KST). */
    fun sinceDaysAgo(days: Int): Instant = today().minusDays((days - 1).toLong()).atStartOfDay(ZONE).toInstant()

    /** 최근 [days]일의 날짜 축(오래된 날 → 오늘). 빈 날도 0으로 채우기 위한 기준. */
    fun dateAxis(days: Int): List<LocalDate> = (0 until days).map { today().minusDays((days - 1 - it).toLong()) }

    /** [instant]가 속한 KST 달력 날짜. */
    fun dateOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()
}
