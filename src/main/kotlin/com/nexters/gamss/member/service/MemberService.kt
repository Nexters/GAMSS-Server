package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
