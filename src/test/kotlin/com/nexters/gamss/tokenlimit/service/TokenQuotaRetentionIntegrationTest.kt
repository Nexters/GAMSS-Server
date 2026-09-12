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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 주체 매핑은 탈퇴 후에도 남아야 재가입 한도 리셋을 막는데, 영구히 두면 탈퇴자를 과거 행적에
 * 언제든 다시 연결할 수 있는 상태가 된다. 하루 한도를 막는 데 필요한 기간만 남기는 것이 이
 * 배치의 계약이다(#222).
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

    /** 활성 회원의 매핑을 지우면 그 회원의 적립이 무효가 되어 한도가 사실상 꺼진다. */
    @Test
    fun `활성 회원의 주체 매핑은 지우지 않는다`() {
        val member = givenMappedMember("active@a.com")

        scheduler.purge()

        assertTrue(identityRepository.existsByMemberId(member.id))
    }

    /** 방금 탈퇴한 회원은 아직 같은 구간이라 연결이 남아 있어야 재가입 이월이 동작한다. */
    @Test
    fun `방금 탈퇴한 회원의 주체 매핑은 남긴다`() {
        val member = givenMappedMember("just-left@a.com")
        withdraw(member.id)

        scheduler.purge()

        assertTrue(identityRepository.existsByMemberId(member.id))
    }

    @Test
    fun `보관 기간이 지난 탈퇴 회원의 주체 매핑은 끊는다`() {
        val member = givenMappedMember("long-gone@a.com")
        withdraw(member.id)
        backdateWithdrawal(member.id, Duration.ofDays(3))

        scheduler.purge()

        assertFalse(identityRepository.existsByMemberId(member.id))
    }

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

    /**
     * 매핑을 만들 때 영속성 컨텍스트를 비우므로 반환된 엔티티는 detached 다. 그대로 withdraw 를
     * 부르면 DB 에 반영되지 않아, 상태가 ACTIVE 인 채로 테스트가 통과해버린다.
     */
    private fun withdraw(memberId: Long) {
        memberRepository.findById(memberId).orElseThrow().withdraw()
        flush()
    }

    /** 탈퇴 시각은 도메인이 now 로 채우므로, 기간이 지난 상태는 SQL 로 되돌려 만든다. */
    private fun backdateWithdrawal(
        memberId: Long,
        ago: Duration,
    ) {
        entityManager
            .createNativeQuery("update members set deleted_at = :deletedAt where id = :id")
            .setParameter("deletedAt", Instant.now().minus(ago))
            .setParameter("id", memberId)
            .executeUpdate()
        flush()
    }

    private fun windowStart(): Instant = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }
}
