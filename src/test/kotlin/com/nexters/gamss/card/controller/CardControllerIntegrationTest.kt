package com.nexters.gamss.card.controller

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals

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

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
