package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.OAuthProvider
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
) {
    @Transactional
    fun findOrCreate(
        provider: OAuthProvider,
        providerId: String,
        email: String?,
    ): Member {
        val existing = memberRepository.findByProviderAndProviderId(provider, providerId)
        if (existing != null) {
            if (email != null && existing.email != email) {
                existing.updateEmail(email)
            }
            return existing
        }
        return memberRepository.save(Member(provider, providerId, email))
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): Member =
        memberRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.MEMBER_NOT_FOUND) }
}
