package com.nexters.gamss.member.service

import java.time.LocalDate

/** 특정 날짜의 가입 회원 수. */
data class DailySignup(
    val date: LocalDate,
    val count: Long,
)
