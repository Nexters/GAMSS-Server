package com.nexters.gamss.card.controller

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class CardShareControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun savedCard(member: Member): Card {
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        return cardRepository.save(
            Card(
                memberId = member.id,
                conversationId = conversation.id,
                emotion = EmotionType.ANGER,
                summary = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
                message = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
                conversationCreatedAt = Instant.parse("2026-07-23T01:00:00Z"),
            ),
        )
    }

    private fun issueShareUrl(
        member: Member,
        card: Card,
    ): String =
        mockMvc
            .post("/api/cards/${card.id}/share") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andReturn()
            .response
            .contentAsString
            .substringAfter("\"shareUrl\":\"")
            .substringBefore("\"")

    @Test
    fun `공유 링크를 발급하면 gamss_kr 주소를 준다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val card = savedCard(member)

        mockMvc
            .post("/api/cards/${card.id}/share") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.shareUrl") { value(org.hamcrest.Matchers.startsWith("https://gamss.kr/c/")) }
            }
    }

    /**
     * 링크를 받은 사람은 앱도 계정도 없을 수 있다. 이 경로가 인증을 요구하게 되면 공유가 통째로 죽는다
     * — SecurityConfig 의 PUBLIC_PATHS 를 건드릴 때 이 테스트가 잡는다.
     */
    @Test
    fun `공유된 카드는 인증 없이 조회된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val card = savedCard(member)
        val token = issueShareUrl(member, card).substringAfterLast("/")

        mockMvc
            .get("/api/cards/shared/$token")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.emotion") { value("ANGER") }
                jsonPath("$.data.emotionLabel") { value("분노") }
                jsonPath("$.data.summary") { value("우산을 안 챙겨서 옷이 다 젖어버렸어요") }
                jsonPath("$.data.date") { value("2026-07-23") }
            }
    }

    /** 링크를 받은 사람이 누가 쓴 카드인지 알 수 있으면 안 된다. */
    @Test
    fun `공유 응답에는 소유자와 식별자가 없다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val card = savedCard(member)
        val token = issueShareUrl(member, card).substringAfterLast("/")

        mockMvc
            .get("/api/cards/shared/$token")
            .andExpect {
                status { isOk() }
                jsonPath("$.data.memberId") { doesNotExist() }
                jsonPath("$.data.id") { doesNotExist() }
                jsonPath("$.data.conversationId") { doesNotExist() }
            }
    }

    /** 발급까지 공개되면 남의 카드로 링크를 만들어 낼 수 있다. */
    @Test
    fun `공유 링크 발급은 인증을 요구한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val card = savedCard(member)

        mockMvc.post("/api/cards/${card.id}/share").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `같은 카드는 몇 번을 요청해도 같은 링크다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val card = savedCard(member)

        assertEquals(issueShareUrl(member, card), issueShareUrl(member, card))
    }

    @Test
    fun `없는 토큰으로 조회하면 404 다`() {
        mockMvc
            .get("/api/cards/shared/Zm9vYmFyYmF6cXV4MTIzNA")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CARD_NOT_FOUND") }
            }
    }

    /**
     * 공개는 **GET 만**이다. 메서드를 지정하지 않으면 이 경로의 모든 메서드가 열려, 나중에 같은
     * 경로에 쓰기 API 를 붙이는 순간 인증 없이 뚫린다. 지금은 핸들러가 없어 시큐리티가 먼저
     * 401 로 끊는다.
     */
    @Test
    fun `공유 경로의 GET 이 아닌 요청은 인증을 요구한다`() {
        mockMvc.post("/api/cards/shared/Zm9vYmFyYmF6cXV4MTIzNA").andExpect { status { isUnauthorized() } }
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
