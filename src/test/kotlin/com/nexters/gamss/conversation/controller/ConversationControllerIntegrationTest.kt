package com.nexters.gamss.conversation.controller

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class ConversationControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

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

    @Test
    fun `conversationId 없이 저장하면 새 채팅방이 만들어지고 응답에 채팅방 ID가 담긴다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"오늘 억울한 일이 있었어"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.conversationId") { isNumber() }
                jsonPath("$.data.senderType") { value("USER") }
                jsonPath("$.data.content") { value("오늘 억울한 일이 있었어") }
            }

        assertEquals(1, conversationRepository.count())
    }

    @Test
    fun `conversationId를 주면 기존 채팅방에 이어서 저장된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"이어서 쓰는 말"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.conversationId") { value(conversation.id) }
            }

        assertEquals(1, conversationRepository.count())
        assertEquals(1, messageRepository.findAllByConversationIdOrderByIdAsc(conversation.id).size)
    }

    @Test
    fun `남의 채팅방에 저장하면 403을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val othersConversation = conversationRepository.save(Conversation(other.id))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${othersConversation.id},"content":"침입"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `없는 채팅방에 저장하면 404를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":99999,"content":"내용"}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CONVERSATION_NOT_FOUND") }
            }
    }

    @Test
    fun `140자를 넘는 내용은 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val tooLong = "가".repeat(141)

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"$tooLong"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `같은 채팅방의 메시지에 답장하면 응답에 답장 대상이 담긴다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val target =
            messageRepository.save(
                Message(conversationId = conversation.id, senderType = SenderType.USER, content = "원본"),
            )

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"답장","repliesToMessageId":${target.id}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.repliesToMessageId") { value(target.id) }
            }
    }

    @Test
    fun `다른 채팅방의 메시지에 답장하면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val myRoom = conversationRepository.save(Conversation(member.id))
        val anotherRoom = conversationRepository.save(Conversation(member.id))
        val otherRoomMessage =
            messageRepository.save(
                Message(conversationId = anotherRoom.id, senderType = SenderType.USER, content = "딴 방"),
            )

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${myRoom.id},"content":"답장","repliesToMessageId":${otherRoomMessage.id}}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `날짜별 채팅방 목록을 조회한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        // 서비스상 하루는 06:00에 시작하므로, 지금 시각의 서비스 날짜는 (현재 KST - 6시간)의 날짜다.
        val serviceDate = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(6).toLocalDate()

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", serviceDate.toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].id") { value(conversation.id) }
            }

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", serviceDate.plusDays(1).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `새벽 6시 이전에 만든 채팅방은 전날 목록으로 조회된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        // 생성 시각을 KST 2026-07-19 02:00(새벽) = UTC 2026-07-18 17:00 으로 조작한다.
        // JDBC 직접 INSERT는 Hibernate와 타임존 변환이 달라질 수 있어 JPQL로 수정한다.
        entityManager.flush()
        entityManager
            .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
            .setParameter("createdAt", Instant.parse("2026-07-18T17:00:00Z"))
            .setParameter("id", conversation.id)
            .executeUpdate()
        entityManager.clear()

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", "2026-07-18")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
            }

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", "2026-07-19")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `날짜 형식이 잘못되면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", "2026-13-99")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `날짜를 누락하면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `채팅방의 메시지를 작성순으로 조회한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "첫 번째"))
        messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "두 번째"))

        mockMvc
            .get("/api/conversations/${conversation.id}/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(2) }
                jsonPath("$.data[0].content") { value("첫 번째") }
                jsonPath("$.data[1].content") { value("두 번째") }
            }
    }

    @Test
    fun `남의 채팅방 메시지를 조회하면 403을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val othersConversation = conversationRepository.save(Conversation(other.id))

        mockMvc
            .get("/api/conversations/${othersConversation.id}/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `인증 없이 저장하면 401을 반환한다`() {
        mockMvc
            .post("/api/conversations/messages") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"내용"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
