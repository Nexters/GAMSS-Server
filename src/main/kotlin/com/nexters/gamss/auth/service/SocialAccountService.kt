package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.SocialAccount
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.service.MemberService
import org.springframework.dao.DataIntegrityViolationException
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
        // 동시 최초 로그인 시 (provider, providerId) 유니크 제약에 걸릴 수 있다.
        // 영속성 예외를 도메인 예외로 번역해, 재시도 판단이 특정 영속성 기술에 의존하지 않게 한다.
        try {
            socialAccountRepository.save(SocialAccount(member.id, storedProvider, providerId))
        } catch (e: DataIntegrityViolationException) {
            throw ConcurrentRegistrationException(provider, providerId)
        }
        return member
    }
}
