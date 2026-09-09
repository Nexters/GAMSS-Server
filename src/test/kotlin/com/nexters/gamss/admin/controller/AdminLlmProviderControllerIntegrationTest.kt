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
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class AdminLlmProviderControllerIntegrationTest {
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
    fun `시딩된 경로와 선택 가능한 경로를 반환한다`() {
        mockMvc
            .get("/api/admin/llm-settings/provider") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.provider") { value("AI_STUDIO") }
                jsonPath("$.data.availableProviders") { value(listOf("AI_STUDIO", "VERTEX_AI")) }
            }
    }

    @Test
    fun `인증 설정이 있는 경로로는 전환된다`() {
        switchTo("AI_STUDIO").andExpect {
            status { isOk() }
            jsonPath("$.data.provider") { value("AI_STUDIO") }
        }
    }

    @Test
    fun `인증 설정이 없는 경로로 전환하면 400 INVALID_INPUT`() {
        // 테스트 환경에는 Vertex 서비스 계정 키가 없다. 전환이 막혀야 생성이 죽지 않는다.
        switchTo("VERTEX_AI").andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
        }

        mockMvc
            .get("/api/admin/llm-settings/provider") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect { jsonPath("$.data.provider") { value("AI_STUDIO") } }
    }

    @Test
    fun `인증 없이 조회하면 401`() {
        mockMvc
            .get("/api/admin/llm-settings/provider")
            .andExpect { status { isUnauthorized() } }
    }

    private fun switchTo(provider: String) =
        mockMvc.put("/api/admin/llm-settings/provider") {
            header(HttpHeaders.AUTHORIZATION, adminBearer())
            contentType = MediaType.APPLICATION_JSON
            content = """{"provider":"$provider"}"""
        }

    private fun adminBearer(): String = "Bearer ${jwtIssuer.issueAdminToken("admin@gamss.kr")}"
}
