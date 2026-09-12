package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 한도는 하루짜리라 지난 구간의 사용량은 더 쓸 데가 없다. 남겨두면 주체별로 행이 무한히 쌓이는데,
 * 특히 탈퇴·재가입을 반복하는 쪽이 만드는 행은 우리가 막으려는 그 행위의 부산물이다(#222).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class TokenQuotaRetentionIntegrationTest {
    @Autowired
    private lateinit var scheduler: TokenQuotaRetentionScheduler

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var usageRepository: TokenQuotaUsageRepository

    @Autowired
    private lateinit var tokenPolicyService: TokenPolicyService

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `현재 구간의 사용량은 지우지 않는다`() {
        val member = givenMappedMember("current@a.com")
        usageRepository.addUsedTokens(member.id, windowStart(), 500)
        flush()

        scheduler.purge()

        assertEquals(500, usageRepository.findUsedTokens(member.id, windowStart()))
    }

    @Test
    fun `보관 기간이 지난 구간의 사용량은 지운다`() {
        val member = givenMappedMember("stale@a.com")
        val oldWindow = windowStart().minus(Duration.ofDays(3))
        usageRepository.addUsedTokens(member.id, oldWindow, 500)
        flush()

        scheduler.purge()

        assertEquals(0, usageRepository.findUsedTokens(member.id, oldWindow))
    }

    private fun givenMappedMember(email: String): Member {
        val member = memberRepository.save(Member(email))
        identityRepository.save(MemberSocialIdentity(member.id, "retention-subject-%064d".format(member.id).takeLast(64)))
        flush()
        return member
    }

    private fun windowStart(): Instant = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
