package com.nexters.gamss.conversation.controller

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.FakeCommentGenerator
import com.nexters.gamss.support.FakeCommentGeneratorConfig
import com.nexters.gamss.support.TestcontainersConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hamcrest.Matchers.greaterThan
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfig::class, FakeCommentGeneratorConfig::class)
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

    @Autowired
    private lateinit var commentGenerator: CommentGenerator

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        (commentGenerator as FakeCommentGenerator).shouldFail = false
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
                jsonPath("$.data.message.conversationId") { isNumber() }
                jsonPath("$.data.message.senderType") { value("USER") }
                jsonPath("$.data.message.content") { value("오늘 억울한 일이 있었어") }
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
                jsonPath("$.data.message.conversationId") { value(conversation.id) }
            }

        assertEquals(1, conversationRepository.count())
        // 저장과 함께 캐릭터 댓글 생성까지 동기로 처리되므로, 유저 메시지 1건 외에 생성된 댓글도 함께 저장된다.
        assertEquals(
            1,
            messageRepository.findAllByConversationIdOrderByIdAsc(conversation.id).count { it.senderType == SenderType.USER },
        )
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
    fun `캐릭터 댓글에 답장하면 응답에 답장 대상이 담긴다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary =
            messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "원본"))
        val target =
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.CHARACTER,
                    emotionType = EmotionType.JOY,
                    content = "댓글",
                    rootMessageId = diary.id,
                ),
            )

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"답장","repliesToMessageId":${target.id}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.message.repliesToMessageId") { value(target.id) }
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
    fun `캐릭터 메시지가 아닌 대상에 답장하면 400을 반환하고 저장되지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary =
            messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "원본"))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"답장","repliesToMessageId":${diary.id}}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }

        assertEquals(1, messageRepository.findAllByConversationIdOrderByIdAsc(conversation.id).size)
    }

    @Test
    fun `일기를 저장하면 같은 요청 안에서 캐릭터 댓글까지 생성되어 함께 반환된다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"오늘 억울한 일이 있었어"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.commentStatus") { value("DONE") }
                jsonPath("$.data.comments.length()") { value(greaterThan(0)) }
                jsonPath("$.data.usedTokens") { value(10) }
            }
    }

    @Test
    fun `답글을 저장하면 같은 요청 안에서 캐릭터 재응답까지 생성되어 함께 반환된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary =
            messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "일기"))
        val characterComment =
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.CHARACTER,
                    emotionType = EmotionType.JOY,
                    content = "잘했다!",
                    rootMessageId = diary.id,
                ),
            )

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"conversationId":${conversation.id},"content":"고마워","repliesToMessageId":${characterComment.id}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.commentStatus") { value("DONE") }
                jsonPath("$.data.comments.length()") { value(1) }
                jsonPath("$.data.comments[0].content") { value("재응답 텍스트") }
                jsonPath("$.data.usedTokens") { value(5) }
            }
    }

    @Test
    fun `LLM 생성이 재시도까지 실패해도 저장은 유지되고 commentStatus=FAILED로 구분된다`() {
        (commentGenerator as FakeCommentGenerator).shouldFail = true
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"오늘 억울한 일이 있었어"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.message.content") { value("오늘 억울한 일이 있었어") }
                jsonPath("$.data.commentStatus") { value("FAILED") }
                jsonPath("$.data.comments.length()") { value(0) }
            }

        val conversationId = conversationRepository.findAll().first().id
        assertEquals(1, messageRepository.findAllByConversationIdOrderByIdAsc(conversationId).size)
    }

    @Test
    fun `날짜별 채팅방 목록을 조회한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val today = LocalDateTime.now(ZoneId.of("Asia/Seoul")).toLocalDate()

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", today.toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].id") { value(conversation.id) }
            }

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", today.plusDays(1).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `자정 정각에 만든 채팅방은 그 날짜로 조회되고, 자정 직전은 전날로 조회된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val midnight = conversationRepository.save(Conversation(member.id))
        val justBeforeMidnight = conversationRepository.save(Conversation(member.id))
        // KST 2026-07-19 00:00:00 = UTC 2026-07-18 15:00:00, KST 2026-07-18 23:59:59 = UTC 2026-07-18 14:59:59.
        // JDBC 직접 INSERT는 Hibernate와 타임존 변환이 달라질 수 있어 JPQL로 수정한다.
        entityManager.flush()
        entityManager
            .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
            .setParameter("createdAt", Instant.parse("2026-07-18T15:00:00Z"))
            .setParameter("id", midnight.id)
            .executeUpdate()
        entityManager
            .createQuery("update Conversation c set c.createdAt = :createdAt where c.id = :id")
            .setParameter("createdAt", Instant.parse("2026-07-18T14:59:59Z"))
            .setParameter("id", justBeforeMidnight.id)
            .executeUpdate()
        entityManager.clear()

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", "2026-07-19")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].id") { value(midnight.id) }
            }

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", "2026-07-18")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(1) }
                jsonPath("$.data[0].id") { value(justBeforeMidnight.id) }
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

    @Test
    fun `채팅방을 종료하면 200과 status ENDED를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .post("/api/conversations/${conversation.id}/end") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(conversation.id) }
                jsonPath("$.data.status") { value("ENDED") }
            }
    }

    @Test
    fun `이미 종료된 채팅방을 다시 종료하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })

        mockMvc
            .post("/api/conversations/${conversation.id}/end") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_ENDED") }
            }
    }

    @Test
    fun `종료된 채팅방에 메시지를 저장하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"종료된 방에 쓰기"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ENDED") }
            }
    }

    @Test
    fun `남의 채팅방을 종료하면 403을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val othersConversation = conversationRepository.save(Conversation(other.id))

        mockMvc
            .post("/api/conversations/${othersConversation.id}/end") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `없는 채팅방을 종료하면 404를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/conversations/99999/end") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CONVERSATION_NOT_FOUND") }
            }
    }

    @Test
    fun `채팅방을 삭제하면 200과 status DELETED를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .delete("/api/conversations/${conversation.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(conversation.id) }
                jsonPath("$.data.status") { value("DELETED") }
            }
    }

    @Test
    fun `종료된 채팅방도 삭제할 수 있다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { end() })

        mockMvc
            .delete("/api/conversations/${conversation.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("DELETED") }
            }
    }

    @Test
    fun `이미 삭제된 채팅방을 다시 삭제하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { delete() })

        mockMvc
            .delete("/api/conversations/${conversation.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `남의 채팅방을 삭제하면 403을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val othersConversation = conversationRepository.save(Conversation(other.id))

        mockMvc
            .delete("/api/conversations/${othersConversation.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `없는 채팅방을 삭제하면 404를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .delete("/api/conversations/99999") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("CONVERSATION_NOT_FOUND") }
            }
    }

    @Test
    fun `삭제된 채팅방을 종료하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { delete() })

        mockMvc
            .post("/api/conversations/${conversation.id}/end") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `삭제된 채팅방에 메시지를 저장하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { delete() })

        mockMvc
            .post("/api/conversations/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"conversationId":${conversation.id},"content":"삭제된 방에 쓰기"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `삭제된 채팅방 메시지를 조회하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { delete() })

        mockMvc
            .get("/api/conversations/${conversation.id}/messages") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `삭제된 채팅방의 메시지에 댓글 생성을 요청하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary =
            messageRepository.save(
                Message(conversationId = conversation.id, senderType = SenderType.USER, content = "삭제 전에 쓴 일기"),
            )
        conversationRepository.save(conversation.apply { delete() })

        mockMvc
            .post("/api/conversations/messages/comments") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"messageId":${diary.id}}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `삭제된 채팅방의 답글에 재응답 생성을 요청하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary =
            messageRepository.save(Message(conversationId = conversation.id, senderType = SenderType.USER, content = "일기"))
        val characterComment =
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.CHARACTER,
                    emotionType = EmotionType.JOY,
                    content = "댓글",
                    rootMessageId = diary.id,
                ),
            )
        val reply =
            messageRepository.save(
                Message(
                    conversationId = conversation.id,
                    senderType = SenderType.USER,
                    content = "답장",
                    repliesToMessageId = characterComment.id,
                ),
            )
        conversationRepository.save(conversation.apply { delete() })

        mockMvc
            .post("/api/conversations/messages/comments/${reply.id}") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    @Test
    fun `삭제된 채팅방은 날짜별 목록 조회에 나타나지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))
        conversationRepository.save(Conversation(member.id).apply { delete() })
        val today = LocalDateTime.now(ZoneId.of("Asia/Seoul")).toLocalDate()

        mockMvc
            .get("/api/conversations") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                param("date", today.toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `새로 만든 채팅방의 제목은 null이다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        assertNull(conversationRepository.findById(conversation.id).get().title)
    }

    @Test
    fun `제목을 지정하면 응답과 저장소에 반영된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .patch("/api/conversations/${conversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"비 오는 날의 짜증"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(conversation.id) }
                jsonPath("$.data.title") { value("비 오는 날의 짜증") }
            }

        assertEquals(
            "비 오는 날의 짜증",
            conversationRepository
                .findById(conversation.id)
                .get()
                .title
                ?.value,
        )
    }

    @Test
    fun `제목을 여러 번 수정할 수 있다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        fun patchTitle(title: String) =
            mockMvc.patch("/api/conversations/${conversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"$title"}"""
            }

        patchTitle("첫 제목").andExpect { status { isOk() } }
        patchTitle("바꾼 제목").andExpect {
            status { isOk() }
            jsonPath("$.data.title") { value("바꾼 제목") }
        }

        assertEquals(
            "바꾼 제목",
            conversationRepository
                .findById(conversation.id)
                .get()
                .title
                ?.value,
        )
    }

    @Test
    fun `제목이 100자를 넘으면 INVALID_CONVERSATION_TITLE`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .patch("/api/conversations/${conversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"${"가".repeat(101)}"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_CONVERSATION_TITLE") }
            }
    }

    @Test
    fun `공백만 있는 제목이면 INVALID_CONVERSATION_TITLE`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))

        mockMvc
            .patch("/api/conversations/${conversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_CONVERSATION_TITLE") }
            }
    }

    @Test
    fun `남의 채팅방 제목을 수정하면 403을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val othersConversation = conversationRepository.save(Conversation(other.id))

        mockMvc
            .patch("/api/conversations/${othersConversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"침입"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("CONVERSATION_ACCESS_DENIED") }
            }
    }

    @Test
    fun `삭제된 채팅방 제목을 수정하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id).apply { delete() })

        mockMvc
            .patch("/api/conversations/${conversation.id}/title") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"바꿔보자"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("CONVERSATION_ALREADY_DELETED") }
            }
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
