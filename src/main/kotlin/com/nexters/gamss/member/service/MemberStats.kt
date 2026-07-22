package com.nexters.gamss.member.service

/** 백오피스 대시보드용 회원 통계 집계 결과. */
data class MemberStats(
    val total: Long,
    val active: Long,
    val withdrawn: Long,
    val dailySignups: List<DailySignup>,
)
