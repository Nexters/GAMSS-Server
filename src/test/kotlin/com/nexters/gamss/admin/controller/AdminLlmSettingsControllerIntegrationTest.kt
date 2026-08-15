package com.nexters.gamss.admin.controller

import com.nexters.gamss.global.security.JwtIssuer
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

// 시딩(V22 이후) 여부와 무관하게 동작하도록, 버전은 절대값 대신 현재 최신 버전에 상대적으로 검증한다.
@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class AdminLlmSettingsControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
    fun `프롬프트를 저장하면 로그인한 관리자가 리비전 기록자로 남는다`() {
        val base = latestVersion()

        savePrompt("통합 테스트 프롬프트")

        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].version") { value(base + 1) }
                jsonPath("$.data.content[0].savedBy") { value("admin@gamss.kr") }
            }
    }

    @Test
    fun `리비전을 복원하면 현재 프롬프트가 그 버전 내용으로 바뀌고 출처가 남는다`() {
        val base = latestVersion()
        savePrompt("첫 번째 프롬프트")
        savePrompt("두 번째 프롬프트")

        val firstRevisionId =
            revisions()
                .path("content")[1]
                .path("id")
                .asLong()

        mockMvc
            .post("/api/admin/llm-settings/prompt/revisions/$firstRevisionId/restore") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.systemPrompt") { value("첫 번째 프롬프트") }
            }

        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.content[0].version") { value(base + 3) }
                jsonPath("$.data.content[0].restoredFromVersion") { value(base + 1) }
            }
    }

    @Test
    fun `리비전 목록의 page가 음수면 400 INVALID_INPUT`() {
        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT&page=-1") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `리비전 목록의 size가 상한을 넘으면 400 INVALID_INPUT`() {
        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT&size=51") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `카드 감정 프롬프트도 조회·수정되고 리비전이 남는다`() {
        // 백오피스 API는 PromptType 제네릭이라 enum·시딩만으로 신규 타입이 동작해야 한다(#134·#135).
        mockMvc
            .get("/api/admin/llm-settings/prompt?promptType=CARD_EMOTION") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.promptType") { value("CARD_EMOTION") }
                // 본문 문구가 아니라 "시딩된 값이 있다"까지만 본다 — 프롬프트 튜닝으로 다시 시드되면
                // (V26이 캐릭터 개편으로 COMMON·COMMENT·CARD 본문을 통째로 갈아치운 선례가 있다)
                // 조회·수정·리비전과 무관한 이유로 이 테스트가 깨진다.
                jsonPath("$.data.systemPrompt") { isNotEmpty() }
            }

        mockMvc
            .put("/api/admin/llm-settings/prompt") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                content = """{"promptType":"CARD_EMOTION","systemPrompt":"수정한 감정 분류 프롬프트"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.systemPrompt") { value("수정한 감정 분류 프롬프트") }
            }

        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=CARD_EMOTION") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].savedBy") { value("admin@gamss.kr") }
            }
    }

    @Test
    fun `인증 없이 리비전 목록을 조회하면 401`() {
        mockMvc
            .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT")
            .andExpect { status { isUnauthorized() } }
    }

    private fun savePrompt(content: String) {
        mockMvc
            .put("/api/admin/llm-settings/prompt") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
                contentType = MediaType.APPLICATION_JSON
                this.content = """{"promptType":"COMMENT","systemPrompt":"$content"}"""
            }.andExpect { status { isOk() } }
    }

    private fun latestVersion(): Int {
        val content = revisions().path("content")
        if (content.isEmpty) {
            return 0
        }
        return content[0].path("version").asInt()
    }

    private fun revisions(): JsonNode {
        val json =
            mockMvc
                .get("/api/admin/llm-settings/prompt/revisions?promptType=COMMENT") {
                    header(HttpHeaders.AUTHORIZATION, adminBearer())
                }.andReturn()
                .response
                .contentAsString
        return objectMapper.readTree(json).path("data")
    }

    private fun adminBearer(): String = "Bearer ${jwtIssuer.issueAdminToken("admin@gamss.kr")}"
}
