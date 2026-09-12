package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.SocialAccount
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.member.service.MemberSocialIdentityService
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
    private val memberSocialIdentityService: MemberSocialIdentityService,
) {
    @Transactional
    fun resolveMember(
        provider: SocialProvider,
        providerId: String,
        email: String?,
        name: String?,
    ): ResolvedMember {
        val storedProvider = provider.name
        val socialAccount = socialAccountRepository.findByProviderAndProviderId(storedProvider, providerId)
        if (socialAccount != null) {
            // 기존 회원도 매핑을 확인한다. 이 기능 배포 전에 가입한 회원은 매핑이 없어, 그대로 두면
            // 사용량이 집계되지 않아 한도가 그 회원에게만 꺼진다(#222).
            memberSocialIdentityService.ensureMapped(socialAccount.memberId, storedProvider, providerId)
            return ResolvedMember(memberService.getById(socialAccount.memberId), isNewMember = false)
        }
        val member = memberService.create(email, name)
        // 동시 최초 로그인 시 (provider, providerId) 유니크 제약에 걸릴 수 있다.
        // 영속성 예외를 도메인 예외로 번역해, 재시도 판단이 특정 영속성 기술에 의존하지 않게 한다.
        try {
            socialAccountRepository.save(SocialAccount(member.id, storedProvider, providerId))
        } catch (e: DataIntegrityViolationException) {
            throw ConcurrentRegistrationException(provider, providerId)
        }
        // 소셜 계정 저장 뒤에 매핑한다. 앞이 유니크 제약에 걸려 되돌아갈 회원에게 매핑을 달아두면,
        // 재시도로 만들어진 진짜 회원이 아닌 쪽에 주체가 붙는다.
        memberSocialIdentityService.ensureMapped(member.id, storedProvider, providerId)
        return ResolvedMember(member, isNewMember = true)
    }
}
