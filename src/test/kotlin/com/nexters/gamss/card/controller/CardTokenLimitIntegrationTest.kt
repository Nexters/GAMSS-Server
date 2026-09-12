package com.nexters.gamss.card.controller

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.FakeCardMessageGeneratorConfig
import com.nexters.gamss.support.FakeEmotionExtractorConfig
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.tokenlimit.service.DailyTokenLimitService
import com.nexters.gamss.tokenlimit.service.TokenPolicyService
import com.nexters.gamss.tokenlimit.service.TokenQuotaRecorder
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 일일 토큰 상한을 **실제로 켜고** 카드 생성을 확인한다.
 *
 * 단위 테스트로는 이 시나리오를 만들 수 없다 — [com.nexters.gamss.card.service.CardService] 가
 * [DailyTokenLimitService] 를 더 이상 받지 않아 "한도를 다 쓴 회원" 이라는 상태 자체를 주입할
 * 곳이 없다. 그래서 쿼터를 상한까지 채운 회원으로 API 를 부른다.
 *
 * 상한은 `gamss.token-limit.enabled` 기본값이 false 라 평소엔 꺼져 있다. 켜지 않으면
 * [DailyTokenLimitService.isWithinLimit] 이 항상 true 라 아래 테스트가 **통과해도 아무것도
 * 증명하지 못한다**. 그래서 프로퍼티로 켜고, 카드를 만들기 전에 정말 한도를 넘긴 상태인지
 * 먼저 단언한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, FakeCardMessageGeneratorConfig::class, FakeEmotionExtractorConfig::class)
@TestPropertySource(properties = ["gamss.token-limit.enabled=true"])
@Transactional
class CardTokenLimitIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var memberSocialIdentityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var tokenQuotaRecorder: TokenQuotaRecorder

    @Autowired
    private lateinit var dailyTokenLimitService: DailyTokenLimitService

    @Autowired
    private lateinit var tokenPolicyService: TokenPolicyService

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    /**
     * 상한을 꽉 채운 회원.
     *
     * 적립 경로([TokenQuotaRecorder])를 그대로 써서 채운다 - 쿼터 행을 직접 넣으면 주체 매핑을
     * 거치는 실제 경로가 빠져, 판정만 맞고 적립이 깨진 상태를 통과시킨다.
     */
    private fun memberWithExhaustedTokens(email: String): Member {
        val member = memberRepository.save(Member(email))
        memberSocialIdentityRepository.save(MemberSocialIdentity(member.id, subjectKeyFor(member)))
        tokenQuotaRecorder.record(member.id, tokenPolicyService.current().dailyTokenLimit.toInt())
        assertFalse(
            dailyTokenLimitService.isWithinLimit(member.id),
            "이 테스트의 전제가 깨졌다 — 한도를 넘긴 상태를 만들지 못했다",
        )
        return member
    }

    /** 회원마다 다른 주체. 실제 값은 소셜 신원의 HMAC 이지만, 여기서 필요한 것은 유일성뿐이다. */
    private fun subjectKeyFor(member: Member): String = "test-subject-%064d".format(member.id).takeLast(64)

    /**
     * 카드에서 막으면 대화는 이미 종료(커밋)된 뒤라 "종료됐는데 카드 없는" 방이 남고, 그 방은
     * 미완성 목록에서도 캘린더에서도 빠져 사용자가 재시도할 방법조차 없다.
     */
    @Test
    fun `일일 토큰을 다 쓴 회원도 카드를 만들 수 있다`() {
        val member = memberWithExhaustedTokens("card-over-limit@test.com")
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"오늘 있었던 일"}"""
            }.andExpect {
                status { isOk() }
            }
    }

    /** 카드를 만들었다고 그날의 댓글 한도가 깎이면 안 된다. */
    @Test
    fun `카드를 만들어도 회원의 사용량은 늘지 않는다`() {
        val member = memberWithExhaustedTokens("card-usage-unchanged@test.com")
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        val before = dailyTokenLimitService.usageFor(member.id).usedTokens

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"오늘 있었던 일"}"""
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        assertEquals(before, dailyTokenLimitService.usageFor(member.id).usedTokens)
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
