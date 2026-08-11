package com.nexters.gamss.admin.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.FakeCommentGeneratorConfig
import com.nexters.gamss.support.TestcontainersConfig
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

@SpringBootTest
@Import(TestcontainersConfig::class, FakeCommentGeneratorConfig::class)
@Transactional
class AdminPromptPreviewControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var memberRepository: MemberRepository

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
    fun `고정한 조건으로 미리보기를 생성하면 피드와 메타데이터가 돌아온다`() {
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"diaryContent":"오늘 억울한 일이 있었다",
                     "commentPrompt":"시험용 댓글 프롬프트",
                     "characters":["JOY","ANGER"],
                     "tikitakaCount":1}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.comments.length()") { value(2) }
                jsonPath("$.data.tikitaka.length()") { value(1) }
                jsonPath("$.data.characters.length()") { value(2) }
                jsonPath("$.data.validationError") { doesNotExist() }
                jsonPath("$.data.generationError") { doesNotExist() }
                jsonPath("$.data.systemPrompt") { exists() }
                jsonPath("$.data.userContent") { exists() }
            }
    }

    @Test
    fun `답장 미리보기는 대상 캐릭터의 재응답을 돌려준다`() {
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview/reply") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"diaryContent":"오늘 억울한 일이 있었다",
                     "character":"ANGER",
                     "characterComment":"누가 그랬어, 화난다!",
                     "userReply":"고마워, 네 말 들으니 낫다"}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.character") { value("ANGER") }
                jsonPath("$.data.replyText") { value("재응답 텍스트") }
                jsonPath("$.data.generationError") { doesNotExist() }
            }
    }

    @Test
    fun `샘플 일기가 비어 있으면 400 INVALID_INPUT`() {
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                // characters를 채워 diaryContent의 @NotBlank 위반만 남긴다 - 원인을 분리해야 검증 제거 회귀를 잡는다.
                content = """{"diaryContent":"","characters":["JOY"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `답장 미리보기에 character가 없으면 400 INVALID_INPUT`() {
        // 컨트롤러의 checkNotNull이 아니라 Bean Validation이 먼저 막는 계약을 고정한다(@NotNull 제거 회귀 방지).
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview/reply") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"diaryContent":"샘플","characterComment":"댓글","userReply":"답장"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `일반 회원 토큰으로 호출하면 403`() {
        val member = memberRepository.save(Member("user@a.com"))

        mockMvc
            .post("/api/admin/llm-settings/prompt/preview") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(member.id)}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"diaryContent":"샘플","characters":["JOY"]}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("ACCESS_DENIED") }
            }
    }

    @Test
    fun `인증 없이 호출하면 401`() {
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"diaryContent":"샘플"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private fun adminBearer(): String = "Bearer ${jwtIssuer.issueAdminToken("admin@gamss.kr")}"
}
