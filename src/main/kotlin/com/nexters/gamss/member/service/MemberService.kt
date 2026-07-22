package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Service
class MemberService(
    private val memberRepository: MemberRepository,
) {
    @Transactional
    fun create(email: String?): Member = memberRepository.save(Member(email))

    @Transactional(readOnly = true)
    fun getById(id: Long): Member =
        memberRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

    // 백오피스 회원 목록 조회. 빈 검색어는 전체 조회로, status null 은 모든 상태로 취급한다.
    @Transactional(readOnly = true)
    fun search(
        keyword: String?,
        status: MemberStatus?,
        pageable: Pageable,
    ): Page<Member> = memberRepository.search(keyword?.takeIf { it.isNotBlank() }, status, pageable)

    // 백오피스 대시보드 통계. 가입 추이는 최근 days 일치를 KST 날짜 기준으로 집계하고 빈 날은 0으로 채운다.
    @Transactional(readOnly = true)
    fun getStats(days: Int): MemberStats {
        val total = memberRepository.count()
        val active = memberRepository.countByStatus(MemberStatus.ACTIVE)
        val withdrawn = memberRepository.countByStatus(MemberStatus.WITHDRAWN)

        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val from = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val countsByDate =
            memberRepository
                .findCreatedAtsSince(from)
                .groupingBy { it.atZone(zone).toLocalDate() }
                .eachCount()
        val dailySignups =
            (0 until days).map { offset ->
                val date = today.minusDays((days - 1 - offset).toLong())
                DailySignup(date, (countsByDate[date] ?: 0).toLong())
            }

        return MemberStats(total, active, withdrawn, dailySignups)
    }

    @Transactional
    fun updateNickname(
        id: Long,
        nickname: Nickname,
    ): Member {
        val member = getById(id)
        member.updateNickname(nickname)
        return member
    }

    @Transactional
    fun withdraw(id: Long) {
        val member = getById(id)
        if (member.isWithdrawn()) {
            throw BusinessException(ErrorCode.ALREADY_WITHDRAWN)
        }
        member.withdraw()
    }
}
