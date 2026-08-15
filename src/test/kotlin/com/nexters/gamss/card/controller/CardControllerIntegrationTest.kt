package com.nexters.gamss.card.controller

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.FakeCardMessageGeneratorConfig
import com.nexters.gamss.support.FakeEmotionExtractorConfig
import com.nexters.gamss.support.TestcontainersConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hamcrest.Matchers.not
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
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class, FakeCardMessageGeneratorConfig::class, FakeEmotionExtractorConfig::class)
@Transactional
class CardControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

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
                // 카드에는 원본이 아니라 LLM이 다듬은 한 줄이 들어간다.
                jsonPath("$.data.summary") { value(not(summary)) }
            }

        entityManager.flush()
        entityManager.clear()

        // 대화방 요약은 다른 채팅방 댓글의 '과거 맥락'으로 쓰이므로 클라이언트 원본 그대로 남아야 한다.
        val persistedConversation = conversationRepository.findById(conversation.id).orElseThrow()
        assertEquals(summary, persistedConversation.summary)

        // 클라이언트가 어느 필드를 읽든 같은 문구가 보여야 한다(Card KDoc 참고).
        val persistedCard = cardRepository.findAll().single { it.conversationId == conversation.id }
        assertEquals(persistedCard.summary, persistedCard.message)
        assertEquals(1, cardRepository.count())
    }

    @Test
    fun `emotion 없이 요청하면 서버가 유저 메시지로 감정을 추출해 카드를 생성한다`() {
        val member = memberRepository.save(Member("card-emotion-fallback@test.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        messageRepository.save(Message(conversation.id, SenderType.USER, content = "오늘 하루종일 우울했다"))

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"summary":"우울했던 하루"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.emotion") { value(EmotionType.SADNESS.name) }
            }

        assertEquals(1, cardRepository.count())
    }

    @Test
    fun `summary가 2000자면 카드가 생성된다`() {
        val member = memberRepository.save(Member("summary-2000@test.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        val summary = "가".repeat(2000)

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"$summary"}"""
            }.andExpect {
                status { isOk() }
            }

        // 요청 상한(2000자)과 카드에 남는 한 줄의 상한(50자)은 별개다.
        val card = cardRepository.findAll().single { it.conversationId == conversation.id }
        assertTrue(card.summary.length <= CardSummary.MAX_LENGTH)
    }

    @Test
    fun `summary가 2000자를 넘으면 400을 반환한다`() {
        val member = memberRepository.save(Member("summary-2001@test.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        val summary = "가".repeat(2001)

        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"conversationId":${conversation.id},"emotion":"ANGER","summary":"$summary"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }

        assertEquals(0, cardRepository.count())
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
    fun `대화방을 먼저 삭제하면 카드도 함께 지워져 카드 삭제는 409가 된다`() {
        val member = memberRepository.save(Member("convfirst@test.com"))
        val card = createCardVia(member, "대화방 먼저 삭제")

        mockMvc
            .delete("/api/conversations/${card.conversationId}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        // 방 삭제가 카드까지 지우므로 이 시점에 카드는 이미 삭제 상태다. 사용자 화면에서는 이미
        // 사라진 카드라 실제로 도달하는 경로는 아니고, 데이터 상태와 응답이 어긋나지 않는지 본다.
        assertNotNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
        mockMvc
            .delete("/api/cards/${card.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CARD_ALREADY_DELETED") }
            }
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

    // ── 감정별 일괄 삭제 ──

    @Test
    fun `감정별로 삭제하면 그 감정 카드만 사라지고 건수를 돌려준다`() {
        val member = memberRepository.save(Member("bulk@test.com"))
        val anger1 = createCardVia(member, "분노1", EmotionType.ANGER)
        createCardVia(member, "분노2", EmotionType.ANGER)
        val joy = createCardVia(member, "기쁨", EmotionType.JOY)

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.deletedCount") { value(2) }
            }
        entityManager.flush()
        entityManager.clear()

        assertNotNull(cardRepository.findById(anger1.id).orElseThrow().deletedAt, "분노 카드는 삭제됨")
        assertNull(cardRepository.findById(joy.id).orElseThrow().deletedAt, "기쁨 카드는 그대로")
    }

    @Test
    fun `감정별로 삭제하면 그 카드들이 나온 대화방도 함께 삭제된다`() {
        val member = memberRepository.save(Member("bulkcascade@test.com"))
        val anger = createCardVia(member, "분노", EmotionType.ANGER)
        val joy = createCardVia(member, "기쁨", EmotionType.JOY)

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        val angerConversation = conversationRepository.findById(anger.conversationId).orElseThrow()
        val joyConversation = conversationRepository.findById(joy.conversationId).orElseThrow()
        assertEquals(ConversationStatus.DELETED, angerConversation.status, "분노 카드의 대화방은 삭제됨")
        assertEquals(ConversationStatus.ENDED, joyConversation.status, "기쁨 카드의 대화방은 그대로")
    }

    @Test
    fun `대상이 없어도 성공하고 0을 돌려준다 - 연속 호출 안전`() {
        val member = memberRepository.save(Member("empty@test.com"))
        createCardVia(member, "기쁨만 있음", EmotionType.JOY)

        repeat(2) {
            mockMvc
                .delete("/api/cards/emotions/ANGER") {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.deletedCount") { value(0) }
                }
        }
    }

    @Test
    fun `이미 삭제한 카드는 다시 세지 않는다`() {
        val member = memberRepository.save(Member("again@test.com"))
        createCardVia(member, "분노1", EmotionType.ANGER)
        createCardVia(member, "분노2", EmotionType.ANGER)

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { jsonPath("$.data.deletedCount") { value(2) } }
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { jsonPath("$.data.deletedCount") { value(0) } }
    }

    @Test
    fun `다른 회원의 같은 감정 카드는 건드리지 않는다`() {
        val me = memberRepository.save(Member("me-bulk@test.com"))
        val other = memberRepository.save(Member("other-bulk@test.com"))
        createCardVia(me, "내 분노", EmotionType.ANGER)
        val othersCard = createCardVia(other, "남의 분노", EmotionType.ANGER)

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect { jsonPath("$.data.deletedCount") { value(1) } }
        entityManager.flush()
        entityManager.clear()

        assertNull(cardRepository.findById(othersCard.id).orElseThrow().deletedAt)
    }

    @Test
    fun `지원하지 않는 감정 값이면 400을 반환한다`() {
        val member = memberRepository.save(Member("bademotion@test.com"))

        mockMvc
            .delete("/api/cards/emotions/NOT_AN_EMOTION") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `채팅방이 먼저 삭제된 카드는 세지도 지우지도 않는다`() {
        val member = memberRepository.save(Member("predeleted@test.com"))
        val hidden = createCardVia(member, "방부터 지운 분노", EmotionType.ANGER)
        val visible = createCardVia(member, "남아 있는 분노", EmotionType.ANGER)
        conversationRepository.findById(hidden.conversationId).orElseThrow().delete()
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.deletedCount") { value(1) }
            }
        entityManager.flush()
        entityManager.clear()

        // 캘린더에 이미 안 보이는 카드다 — 세면 사용자가 화면에서 본 장수와 응답이 어긋난다.
        assertNull(cardRepository.findById(hidden.id).orElseThrow().deletedAt, "안 보이던 카드는 대상이 아니다")
        assertNotNull(cardRepository.findById(visible.id).orElseThrow().deletedAt, "보이던 카드는 삭제됨")
    }

    @Test
    fun `일괄 삭제도 채팅방의 변경 시각을 남긴다`() {
        val member = memberRepository.save(Member("touched@test.com"))
        val card = createCardVia(member, "분노", EmotionType.ANGER)
        val before = conversationRepository.findById(card.conversationId).orElseThrow().updatedAt

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        // 벌크 UPDATE 는 엔티티를 거치지 않아 @LastModifiedDate 가 돌지 않는다 — 쿼리가 직접
        // 갱신하지 않으면 단건 삭제와 달리 변경 시각이 그대로 남는다.
        val after = conversationRepository.findById(card.conversationId).orElseThrow().updatedAt
        assertTrue(after > before, "삭제 시각이 updatedAt 에 반영되어야 한다")
    }

    @Test
    fun `인증 없이 감정별 삭제를 호출하면 401을 반환한다`() {
        mockMvc
            .delete("/api/cards/emotions/ANGER")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    // ── 전체 삭제 ──

    @Test
    fun `전체 삭제하면 감정과 무관하게 모두 사라지고 건수를 돌려준다`() {
        val member = memberRepository.save(Member("all@test.com"))
        createCardVia(member, "분노", EmotionType.ANGER)
        createCardVia(member, "기쁨", EmotionType.JOY)
        createCardVia(member, "불안", EmotionType.ANXIETY)

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.deletedCount") { value(3) }
            }
        entityManager.flush()
        entityManager.clear()

        assertTrue(
            cardRepository.findAll().filter { it.memberId == member.id }.all { it.isDeleted() },
            "내 카드는 전부 삭제 상태여야 한다",
        )
    }

    @Test
    fun `전체 삭제하면 카드가 나온 대화방도 모두 함께 삭제된다`() {
        val member = memberRepository.save(Member("allcascade@test.com"))
        val anger = createCardVia(member, "분노", EmotionType.ANGER)
        val joy = createCardVia(member, "기쁨", EmotionType.JOY)
        // 카드가 없는 대화방은 삭제 대상이 아니다 — 전체 삭제는 어디까지나 '카드' 삭제다.
        val cardless = conversationRepository.save(Conversation(member.id).apply { end() })

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        listOf(anger.conversationId, joy.conversationId).forEach {
            assertEquals(ConversationStatus.DELETED, conversationRepository.findById(it).orElseThrow().status)
        }
        assertEquals(
            ConversationStatus.ENDED,
            conversationRepository.findById(cardless.id).orElseThrow().status,
            "카드 없는 대화방은 그대로여야 한다",
        )
    }

    @Test
    fun `전체 삭제는 대상이 없어도 성공하고 0을 돌려준다 - 연속 호출 안전`() {
        val member = memberRepository.save(Member("allempty@test.com"))

        repeat(2) {
            mockMvc
                .delete("/api/cards") {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.deletedCount") { value(0) }
                }
        }
    }

    @Test
    fun `전체 삭제는 다른 회원 카드를 건드리지 않는다`() {
        val me = memberRepository.save(Member("me-all@test.com"))
        val other = memberRepository.save(Member("other-all@test.com"))
        createCardVia(me, "내 카드", EmotionType.ANGER)
        val othersCard = createCardVia(other, "남의 카드", EmotionType.JOY)

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect { jsonPath("$.data.deletedCount") { value(1) } }
        entityManager.flush()
        entityManager.clear()

        assertNull(cardRepository.findById(othersCard.id).orElseThrow().deletedAt)
    }

    @Test
    fun `전체 삭제 뒤 감정별 삭제를 호출하면 0을 돌려준다`() {
        val member = memberRepository.save(Member("chain@test.com"))
        createCardVia(member, "분노", EmotionType.ANGER)

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { jsonPath("$.data.deletedCount") { value(1) } }
        entityManager.flush()
        entityManager.clear()

        // 두 벌크 삭제가 같은 가시성 규칙(deletedAt is null)을 쓰는지 확인한다.
        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { jsonPath("$.data.deletedCount") { value(0) } }
    }

    @Test
    fun `감정별 삭제해도 백오피스 생성 이력 집계는 그대로다`() {
        val member = memberRepository.save(Member("emostat@test.com"))
        val card = createCardVia(member, "감정별 통계 유지", EmotionType.ANGER)
        val since = card.conversationCreatedAt.minusSeconds(60)
        val before: Long = cardRepository.countByEmotionSince(since).sumOf { it.count }
        val todayCountBefore = cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60))

        mockMvc
            .delete("/api/cards/emotions/ANGER") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        assertEquals(before, cardRepository.countByEmotionSince(since).sumOf { it.count }, "감정 분포")
        assertEquals(1, cardRepository.findCreatedAtsSince(since).size, "일별 추이")
        assertEquals(
            todayCountBefore,
            cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60)),
            "오늘 카드 수",
        )
    }

    @Test
    fun `전체 삭제해도 백오피스 생성 이력 집계는 그대로다`() {
        val member = memberRepository.save(Member("allstat@test.com"))
        val card = createCardVia(member, "통계 유지", EmotionType.ANGER)
        val since = card.conversationCreatedAt.minusSeconds(60)
        val before: Long = cardRepository.countByEmotionSince(since).sumOf { it.count }
        val todayCountBefore = cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60))

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        assertEquals(before, cardRepository.countByEmotionSince(since).sumOf { it.count }, "감정 분포")
        assertEquals(1, cardRepository.findCreatedAtsSince(since).size, "일별 추이")
        assertEquals(
            todayCountBefore,
            cardRepository.countCreatedBetween(since, card.conversationCreatedAt.plusSeconds(60)),
            "오늘 카드 수",
        )
    }

    @Test
    fun `전체 삭제도 채팅방이 먼저 삭제된 카드는 세지도 지우지도 않는다`() {
        val member = memberRepository.save(Member("allpredeleted@test.com"))
        val hidden = createCardVia(member, "방부터 지운 카드", EmotionType.ANGER)
        val visible = createCardVia(member, "남아 있는 카드", EmotionType.JOY)
        conversationRepository.findById(hidden.conversationId).orElseThrow().delete()
        entityManager.flush()
        entityManager.clear()

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.deletedCount") { value(1) }
            }
        entityManager.flush()
        entityManager.clear()

        // 감정별 삭제와 같은 가시성 규칙이다 — 두 쿼리의 조건은 항상 함께 움직여야 한다.
        assertNull(cardRepository.findById(hidden.id).orElseThrow().deletedAt, "안 보이던 카드는 대상이 아니다")
        assertNotNull(cardRepository.findById(visible.id).orElseThrow().deletedAt, "보이던 카드는 삭제됨")
    }

    @Test
    fun `전체 삭제도 채팅방의 변경 시각을 남긴다`() {
        val member = memberRepository.save(Member("alltouched@test.com"))
        val card = createCardVia(member, "분노", EmotionType.ANGER)
        val before = conversationRepository.findById(card.conversationId).orElseThrow().updatedAt

        mockMvc
            .delete("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect { status { isOk() } }
        entityManager.flush()
        entityManager.clear()

        val after = conversationRepository.findById(card.conversationId).orElseThrow().updatedAt
        assertTrue(after > before, "삭제 시각이 updatedAt 에 반영되어야 한다")
    }

    @Test
    fun `인증 없이 전체 삭제를 호출하면 401을 반환한다`() {
        mockMvc
            .delete("/api/cards")
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
                    jsonPath("$.data.summary") { value(card.summary) }
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

        // 방이 지워지면 카드도 함께 지워진다 — 삭제된 방에 살아 있는 카드를 남기지 않는다.
        assertNotNull(cardRepository.findById(card.id).orElseThrow().deletedAt)
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
        emotion: EmotionType = EmotionType.ANGER,
    ): Card {
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })
        mockMvc
            .post("/api/cards") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"emotion":"$emotion","summary":"$summary"}"""
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
