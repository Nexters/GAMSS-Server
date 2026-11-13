package com.nexters.gamss.admin.controller

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.quality.repository.ConversationViewLogRepository
import com.nexters.gamss.quality.service.QualityJudgeService
import com.nexters.gamss.support.FakeResponseJudgeConfig
import com.nexters.gamss.support.TestcontainersConfig
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class, FakeResponseJudgeConfig::class)
@Transactional
class AdminConversationQualityControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Autowired
    private lateinit var viewLogRepository: ConversationViewLogRepository

    @Autowired
    private lateinit var qualityJudgeService: QualityJudgeService

    @Autowired
    private lateinit var conversationService: ConversationService

    private lateinit var mockMvc: MockMvc

    private lateinit var conversation: Conversation
    private lateinit var diary: Message
    private lateinit var natural: Message
    private lateinit var awkward: Message

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        val member = memberRepository.save(Member("me@a.com"))
        conversation = conversationRepository.save(Conversation(member.id))
        diary = save(SenderType.USER, null, "오늘 팀장한테 깨졌어")
        natural = save(SenderType.CHARACTER, EmotionType.JOY, "괜찮아 잘했어", root = diary.id)
        awkward = save(SenderType.CHARACTER, EmotionType.ANXIETY, "혹시 내일 날씨 어때?", root = diary.id)
        qualityJudgeService.judge(diary.id, qualityJudgeService.currentJudge(), 1)
    }

    @Test
    fun `대화 원문을 열면 응답마다 판정이 붙고 열람 기록이 남는다`() {
        mockMvc
            .get("/api/admin/conversations/${conversation.id}/thread") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.messages.length()") { value(3) }
                jsonPath("$.data.messages[0].response") { doesNotExist() }
                jsonPath("$.data.messages[2].response.kind") { value("COMMENT") }
                jsonPath("$.data.messages[2].response.aiVerdict.verdict") { value("AWKWARD") }
                jsonPath("$.data.messages[2].response.aiVerdict.evidence") { value("날씨") }
            }

        assertEquals(1, viewLogRepository.findAll().count { it.conversationId == conversation.id })
    }

    @Test
    fun `삭제된 대화는 원문을 열 수 없다`() {
        conversationService.deleteConversation(conversation.memberId, conversation.id)

        mockMvc
            .get("/api/admin/conversations/${conversation.id}/thread") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `판정하기 모드에서는 내가 판정하기 전까지 AI 판정을 가리고, 판정하면 함께 돌려준다`() {
        mockMvc
            .get("/api/admin/conversations/${conversation.id}/thread?blind=true") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.messages[2].response.aiVerdict") { doesNotExist() }
                jsonPath("$.data.messages[2].response.aiHidden") { value(true) }
            }

        mockMvc
            .put("/api/admin/conversation-quality/responses/${awkward.id}/human-verdict") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"verdict":"NATURAL"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.aiVerdict.verdict") { value("AWKWARD") }
                jsonPath("$.data.agreesWithAi") { value(false) }
                jsonPath("$.data.mineCount") { value(1) }
            }

        mockMvc
            .get("/api/admin/conversations/${conversation.id}/thread?blind=true") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.messages[2].response.aiVerdict.verdict") { value("AWKWARD") }
                jsonPath("$.data.messages[2].response.myVerdict.verdict") { value("NATURAL") }
            }
    }

    @Test
    fun `어색함으로 판정하면서 유형을 고르지 않으면 400이다`() {
        mockMvc
            .put("/api/admin/conversation-quality/responses/${awkward.id}/human-verdict") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"verdict":"AWKWARD"}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `응답 목록을 AI 판정과 내 판정 여부로 거른다`() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        mockMvc
            .get("/api/admin/conversation-quality/responses?from=$today&to=$today&aiVerdict=AWKWARD") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.totalElements") { value(1) }
                jsonPath("$.data.content[0].messageId") { value(awkward.id) }
                jsonPath("$.data.content[0].aiVerdict.awkwardTypes[0]") { value("OFF_CONTEXT") }
            }

        mockMvc
            .get("/api/admin/conversation-quality/responses?from=$today&to=$today&humanState=MINE_PENDING&emotionType=JOY") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.totalElements") { value(1) }
                jsonPath("$.data.content[0].messageId") { value(natural.id) }
            }
    }

    @Test
    fun `판정하기 다음 대상은 내가 판정하지 않은 응답이다`() {
        mockMvc
            .get("/api/admin/conversation-quality/labeling/next?exclude=${natural.id}") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.messageId") { value(awkward.id) }
                jsonPath("$.data.conversationId") { value(conversation.id) }
                jsonPath("$.data.target") { value(100) }
            }
    }

    @Test
    fun `실험실 입력은 응답을 만든 일기와 캐릭터를 돌려준다`() {
        mockMvc
            .get("/api/admin/conversation-quality/responses/${awkward.id}/playground-input") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.kind") { value("COMMENT") }
                jsonPath("$.data.diary") { value("오늘 팀장한테 깨졌어") }
                jsonPath("$.data.characters.length()") { value(2) }
                jsonPath("$.data.tikitakaCount") { value(0) }
            }
    }

    @Test
    fun `지표는 어색한 응답 비율과 캐릭터별 비율을 돌려준다`() {
        mockMvc
            .get("/api/admin/conversation-quality/stats?days=7") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.judgedCount") { value(2) }
                jsonPath("$.data.awkwardRate") { value(50.0) }
                jsonPath("$.data.byCharacter[?(@.emotionType == 'ANXIETY')].awkward") { value(1) }
                jsonPath("$.data.byType[0].type") { value("OFF_CONTEXT") }
            }
    }

    @Test
    fun `채점 모델을 앱 모델과 따로 바꾸고 자동 채점을 켠다`() {
        mockMvc
            .put("/api/admin/llm-settings/judge") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"model":"gemini-judge-test","autoEnabled":true,"voteCount":3}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.model") { value("gemini-judge-test") }
                jsonPath("$.data.sameAsAppModel") { value(false) }
                jsonPath("$.data.autoEnabled") { value(true) }
            }

        mockMvc
            .get("/api/admin/llm-settings/model") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect { jsonPath("$.data.model") { value(not("gemini-judge-test")) } }
    }

    @Test
    fun `채점 횟수가 범위를 벗어나면 400이다`() {
        mockMvc
            .put("/api/admin/llm-settings/judge") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"model":"gemini-judge-test","autoEnabled":false,"voteCount":9}"""
            }.andExpect { status { isBadRequest() } }
    }

    private fun save(
        senderType: SenderType,
        emotionType: EmotionType?,
        content: String,
        root: Long? = null,
    ): Message = messageRepository.save(Message(conversation.id, senderType, emotionType, content, rootMessageId = root))

    private fun adminBearer(): String = "Bearer ${jwtIssuer.issueAdminToken("admin@gamss.kr")}"
}
