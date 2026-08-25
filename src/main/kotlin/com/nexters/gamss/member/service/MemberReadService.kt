package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberStatus
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 회원을 읽는 창구. 다른 모듈(인증·카드 배치·백오피스)이 [MemberRepository] 를 직접 잡지 않게 한다.
 *
 * 읽기만 둔다. 회원을 만들고 바꾸고 탈퇴시키는 일은 [MemberService] 가 맡는다
 * ([com.nexters.gamss.conversation.service.ConversationReadService] 와 같은 규약).
 */
@Service
@Transactional(readOnly = true)
class MemberReadService(
    private val memberRepository: MemberRepository,
) {
    fun getById(id: Long): Member =
        memberRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }

    /** 백오피스 회원 목록. 빈 검색어는 전체 조회로, [status] null 은 모든 상태로 취급한다. */
    fun search(
        keyword: String?,
        status: MemberStatus?,
        pageable: Pageable,
    ): Page<Member> = memberRepository.search(keyword?.takeIf { it.isNotBlank() }, status, pageable)

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
