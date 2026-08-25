package com.nexters.gamss.member.service

import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 회원이 **얼마나 있는지** 센다. 백오피스 대시보드가 쓴다.
 *
 * 회원 유스케이스([MemberService])와 갈라 둔 이유는 바뀌는 이유가 다르기 때문이다. 여기 있는
 * 것들은 "무엇을 보고 싶은가"가 바뀔 때 함께 바뀌고, 회원 자체의 규칙과는 무관하다.
 */
@Service
@Transactional(readOnly = true)
class MemberStatsService(
    private val memberRepository: MemberRepository,
) {
    /** [from, to) 사이 가입 수. 백오피스 대시보드의 '오늘 신규 가입' KPI. */
    fun countSignupsBetween(
        from: Instant,
        to: Instant,
    ): Long = memberRepository.countCreatedBetween(from, to)

    /** 백오피스 대시보드 통계. 가입 추이는 최근 [days] 일치를 KST 날짜 기준으로 집계하고 빈 날은 0으로 채운다. */
    fun getStats(days: Int): MemberStats {
        val total = memberRepository.count()
        val active = memberRepository.countByStatus(MemberStatus.ACTIVE)
        val withdrawn = memberRepository.countByStatus(MemberStatus.WITHDRAWN)

        val today = LocalDate.now(ZONE)
        val from = today.minusDays((days - 1).toLong()).atStartOfDay(ZONE).toInstant()
        val countsByDate =
            memberRepository
                .findCreatedAtsSince(from)
                .groupingBy { it.atZone(ZONE).toLocalDate() }
                .eachCount()
        val dailySignups =
            (0 until days).map { offset ->
                val date = today.minusDays((days - 1 - offset).toLong())
                DailySignup(date, (countsByDate[date] ?: 0).toLong())
            }

        return MemberStats(total, active, withdrawn, dailySignups)
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
