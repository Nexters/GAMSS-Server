package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.SocialAccount
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 소셜 계정과 회원을 연결한다. 소셜 계정이 없으면 회원을 새로 만들어 연결한다.
 * provider 는 domain(SocialAccount)에 문자열로 저장한다.
 */
@Service
class SocialAccountService(
    private val socialAccountRepository: SocialAccountRepository,
    private val memberService: MemberService,
) {
    @Transactional
    fun resolveMember(
        provider: OAuthProvider,
        providerId: String,
        email: String?,
    ): Member {
        val storedProvider = provider.name
        val socialAccount = socialAccountRepository.findByProviderAndProviderId(storedProvider, providerId)
        if (socialAccount != null) {
            return memberService.getById(socialAccount.memberId)
        }
        val member = memberService.create(email)
        socialAccountRepository.save(SocialAccount(member.id, storedProvider, providerId))
        return member
    }
}
