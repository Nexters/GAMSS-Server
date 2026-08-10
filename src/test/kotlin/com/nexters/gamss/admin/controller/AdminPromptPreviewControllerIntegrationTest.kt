package com.nexters.gamss.admin.controller

import com.nexters.gamss.global.security.JwtIssuer
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
    fun `샘플 일기가 비어 있으면 400 INVALID_INPUT`() {
        mockMvc
            .post("/api/admin/llm-settings/prompt/preview") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"diaryContent":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
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
