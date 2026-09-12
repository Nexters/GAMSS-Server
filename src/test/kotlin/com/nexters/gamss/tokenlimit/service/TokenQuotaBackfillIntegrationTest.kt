package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals

/**
 * 카운터는 배포 순간 비어 있는데 사용량은 이미 있다. 시드가 없으면 배포가 그날 한도를 한 번
 * 리셋해주는 셈이라, 막으려던 어뷰징을 배포가 대신 해준다(#222).
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class TokenQuotaBackfillIntegrationTest {
    @Autowired
    private lateinit var backfill: TokenQuotaBackfill

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var generationLogRepository: GenerationLogRepository

    @Autowired
    private lateinit var usageRepository: TokenQuotaUsageRepository

    @Autowired
    private lateinit var recorder: TokenQuotaRecorder

    @Autowired
    private lateinit var tokenPolicyService: TokenPolicyService

    @Test
    fun `현재 구간의 생성 로그를 쿼터로 시드한다`() {
        val member = givenMappedMember("seed@a.com")
        generationLogRepository.save(generationLog(member.id, GenerationType.COMMENT, usedTokens = 700))

        backfill.seed()

        assertEquals(700, usedTokens(member))
    }

    /** 카드는 예전 합산 쿼리에서도 빠져 있었다. 시드가 없던 사용량을 만들어내면 안 된다. */
    @Test
    fun `카드 생성 로그는 시드하지 않는다`() {
        val member = givenMappedMember("card-seed@a.com")
        generationLogRepository.save(generationLog(member.id, GenerationType.CARD, usedTokens = 5_000))
        generationLogRepository.save(generationLog(member.id, GenerationType.CARD_EMOTION, usedTokens = 3_000))

        backfill.seed()

        assertEquals(0, usedTokens(member))
    }

    /**
     * 이미 적립된 값을 덮어쓰면, 적립은 성공했지만 관측 기록이 실패한 만큼이 사라진다 - 그러면
     * 관측 테이블이 사실상 진실의 원천으로 되돌아간다. 재기동에도 안전해야 한다.
     */
    @Test
    fun `이미 적립된 쿼터는 덮어쓰지 않는다`() {
        val member = givenMappedMember("keep@a.com")
        recorder.record(member.id, 1_000)
        // 관측 기록에는 다른 값이 남아 있는 상황. 시드가 이것으로 갈아치우면 안 된다.
        generationLogRepository.save(generationLog(member.id, GenerationType.COMMENT, usedTokens = 42))

        backfill.seed()

        assertEquals(1_000, usedTokens(member))
    }

    /** 매핑이 없으면 시드할 주체가 없다. 매핑 백필이 먼저 돌아야 하는 이유다. */
    @Test
    fun `주체 매핑이 없는 회원은 시드하지 않는다`() {
        val member = memberRepository.save(Member("unmapped@a.com"))
        generationLogRepository.save(generationLog(member.id, GenerationType.COMMENT, usedTokens = 800))

        backfill.seed()

        assertEquals(0, usedTokens(member))
    }

    /** 지난 구간은 이미 집행이 끝났다. 끌어오면 오늘 한도를 어제 사용량으로 깎는다. */
    @Test
    fun `구간 밖의 생성 로그는 시드하지 않는다`() {
        val member = givenMappedMember("old@a.com")
        val beforeWindow = windowStart().minusSeconds(60)
        generationLogRepository.save(generationLog(member.id, GenerationType.COMMENT, usedTokens = 900, createdAt = beforeWindow))

        backfill.seed()

        assertEquals(0, usedTokens(member))
    }

    private fun givenMappedMember(email: String): Member {
        val member = memberRepository.save(Member(email))
        identityRepository.save(MemberSocialIdentity(member.id, subjectKeyFor(member.id)))
        return member
    }

    private fun usedTokens(member: Member): Long = usageRepository.findUsedTokens(member.id, windowStart())

    private fun windowStart(): Instant = TokenQuotaWindow.startOf(tokenPolicyService.current().resetHour)

    private fun subjectKeyFor(memberId: Long): String = "backfill-subject-%064d".format(memberId).takeLast(64)

    private fun generationLog(
        memberId: Long,
        type: GenerationType,
        usedTokens: Int,
        createdAt: Instant = Instant.now(),
    ): GenerationLog =
        GenerationLog(
            generationType = type,
            model = "test-model",
            memberId = memberId,
            success = true,
            attemptCount = 1,
            usedTokens = usedTokens,
            latencyMs = 100,
            createdAt = createdAt,
        )
}
