package com.nexters.gamss.card.controller

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.FakeCardMessageGeneratorConfig
import com.nexters.gamss.support.TestcontainersConfig
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfig::class, FakeCardMessageGeneratorConfig::class)
@Transactional
class CardControllerIntegrationTest {
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

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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

    @Test
    fun `카드 생성 시 요청한 대화 요약이 Conversation에 저장된다`() {
        val member = memberRepository.save(Member("card-summary@test.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        val summary = "오늘은 회사에서 힘든 일이 있었지만 친구와 통화하며 조금 괜찮아졌다."

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"$summary"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.summary") { value(summary) }
            }

        entityManager.flush()
        entityManager.clear()

        val persistedConversation = conversationRepository.findById(conversation.id).orElseThrow()
        assertEquals(summary, persistedConversation.summary)
        assertEquals(1, cardRepository.count())
    }

    // ── 카드 삭제 ──

    @Test
    fun `카드를 삭제하면 날짜별·월별 조회에서 사라진다`() {
        val member = memberRepository.save(Member("del@test.com"))
        val card = createCardVia(member, "삭제될 카드")
        val date = card.conversationCreatedAt.atZone(KST).toLocalDate()

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .get("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", date.toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
        mockMvc
            .get("/api/cards/monthly") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("yearMonth", "%04d-%02d".format(date.year, date.monthValue))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `카드를 삭제하면 카드가 나온 대화방도 함께 삭제된다`() {
        val member = memberRepository.save(Member("cascade@test.com"))
        val card = createCardVia(member, "대화방까지 삭제")

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        val conversation = conversationRepository.findById(card.conversationId).orElseThrow()
        assertEquals(ConversationStatus.DELETED, conversation.status, "대화방도 삭제 상태여야 한다")
    }

    @Test
    fun `대화방을 먼저 삭제한 뒤에도 카드를 삭제할 수 있다`() {
        val member = memberRepository.save(Member("convfirst@test.com"))
        val card = createCardVia(member, "대화방 먼저 삭제")

        mockMvc
            .delete("/api/conversations/${card.conversationId}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        // 이미 삭제된 방을 다시 delete() 하면 예외라, 카드 삭제가 그 예외에 휘말리면 안 된다.
        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        assertNotNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
    }

    @Test
    fun `삭제해도 행은 남아 백오피스 생성 이력 집계는 그대로다`() {
        val member = memberRepository.save(Member("keep@test.com"))
        val card = createCardVia(member, "통계에 남을 카드")
        val since = card.conversationCreatedAt.minusSeconds(60)
        val before: Long = cardRepository.countByEmotionSince(since).sumOf { it.count }
        val todayCountBefore = cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60))

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        val after: Long = cardRepository.countByEmotionSince(since).sumOf { it.count }
        assertEquals(before, after, "감정 분포")
        assertEquals(1, cardRepository.findCreatedAtsSince(since).size, "일별 추이")
        // '오늘 카드 수' KPI 도 생성 이력이라 삭제로 줄면 안 된다.
        assertEquals(
            todayCountBefore,
            cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60)),
            "오늘 카드 수",
        )
    }

    @Test
    fun `삭제한 카드는 같은 대화방에 다시 만들 수 없다`() {
        val member = memberRepository.save(Member("recreate@test.com"))
        val card = createCardVia(member, "지웠다 다시")

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${card.conversationId},"emotion":"ANGER","summary":"다시 만들기"}"""
            }.andExpect {
                // 카드 삭제가 대화방까지 지우므로 대화방 검증에서 먼저 걸린다. 대화방이 살아 있어도
                // CARD_ALREADY_EXISTS 로 막히는 건 마찬가지고(uk_cards_conversation_id), 어느 쪽이든 409 다.
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `이미 삭제한 카드를 또 삭제하면 409를 반환한다`() {
        val member = memberRepository.save(Member("twice@test.com"))
        val card = createCardVia(member, "두 번 삭제")

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CARD_ALREADY_DELETED") }
            }
    }

    @Test
    fun `남의 카드를 삭제하면 403을 반환한다`() {
        val owner = memberRepository.save(Member("owner@test.com"))
        val other = memberRepository.save(Member("other@test.com"))
        val card = createCardVia(owner, "남의 카드")

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(other))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CARD_ACCESS_DENIED") }
            }
    }

    @Test
    fun `없는 카드를 삭제하면 404를 반환한다`() {
        val member = memberRepository.save(Member("nope@test.com"))

        mockMvc
            .delete("/api/cards/999999") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CARD_NOT_FOUND") }
            }
    }

    @Test
    fun `인증 없이 카드를 삭제하면 401을 반환한다`() {
        mockMvc
            .delete("/api/cards/1")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    // ── 카드 단건 조회 ──

    @Test
    fun `id로 본인 카드를 조회하면 날짜별 조회와 같은 내용을 돌려준다`() {
        val member = memberRepository.save(Member("getone@test.com"))
        val card = createCardVia(member, "조회할 카드")
        val date = card.conversationCreatedAt.atZone(KST).toLocalDate()

        // 두 엔드포인트가 같은 카드를 같은 형태로 돌려주는지가 이 API 의 계약이다 — 필드를 하나씩
        // 확인하면 나중에 CardResponse 가 갈라져도 알 수 없어서 응답 자체를 비교한다.
        val fromDateQuery =
            mockMvc
                .get("/api/cards") {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                    param("date", date.toString())
                }.andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString
                .let { objectMapper.readTree(it).path("data").single() }

        val fromIdQuery =
            mockMvc
                .get("/api/cards/${card.id}") {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.id") { value(card.id) }
                    jsonPath("$.data.summary") { value("조회할 카드") }
                }.andReturn()
                .response
                .contentAsString
                .let { objectMapper.readTree(it).path("data") }

        assertEquals(fromDateQuery, fromIdQuery, "id 조회와 날짜별 조회의 카드 표현이 같아야 한다")
    }

    @Test
    fun `카드 id 형식이 잘못되면 400을 반환한다`() {
        val member = memberRepository.save(Member("getbadid@test.com"))

        mockMvc
            .get("/api/cards/not-a-number") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `없는 카드를 조회하면 404를 반환한다`() {
        val member = memberRepository.save(Member("getnope@test.com"))

        mockMvc
            .get("/api/cards/999999") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CARD_NOT_FOUND") }
            }
    }

    @Test
    fun `남의 카드를 조회하면 403을 반환한다`() {
        val me = memberRepository.save(Member("getme@test.com"))
        val other = memberRepository.save(Member("getother@test.com"))
        val othersCard = createCardVia(other, "남의 카드")

        mockMvc
            .get("/api/cards/${othersCard.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CARD_ACCESS_DENIED") }
            }
    }

    @Test
    fun `삭제한 카드를 id로 조회하면 404를 반환한다`() {
        val member = memberRepository.save(Member("getdeleted@test.com"))
        val card = createCardVia(member, "지워질 카드")

        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        // 날짜별 조회에서 사라진 카드가 id 로만 열리면 사용자가 보는 목록과 어긋난다.
        mockMvc
            .get("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CARD_NOT_FOUND") }
            }
    }

    @Test
    fun `채팅방이 삭제된 카드를 id로 조회하면 404를 반환한다`() {
        val member = memberRepository.save(Member("getconvdeleted@test.com"))
        val card = createCardVia(member, "방이 지워질 카드")

        mockMvc
            .delete("/api/conversations/${card.conversationId}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        // 카드 자체는 deletedAt 이 null 이지만 캘린더에는 이미 나오지 않는다 — 같은 규칙을 따른다.
        assertNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
        mockMvc
            .get("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CARD_NOT_FOUND") }
            }
    }

    @Test
    fun `단건 조회 경로가 월별 조회 경로를 가리지 않는다`() {
        val member = memberRepository.save(Member("getmonthly@test.com"))
        val card = createCardVia(member, "캘린더에 뜰 카드")
        val date = card.conversationCreatedAt.atZone(KST).toLocalDate()

        // GET /api/cards/{cardId} 가 생기면서 /monthly 가 cardId 로 잡히면 400 이 된다.
        mockMvc
            .get("/api/cards/monthly") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("yearMonth", "%04d-%02d".format(date.year, date.monthValue))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
            }
    }

    @Test
    fun `인증 없이 카드를 조회하면 401을 반환한다`() {
        mockMvc
            .get("/api/cards/1")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    /** 실제 생성 엔드포인트로 카드를 만들어, 삭제 대상이 운영 경로와 같은 상태가 되게 한다. */
    private fun createCardVia(
        member: Member,
        summary: String,
    ): Card {
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"$summary"}"""
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()
        return cardRepository.findAll().single { it.conversationId == conversation.id }
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
