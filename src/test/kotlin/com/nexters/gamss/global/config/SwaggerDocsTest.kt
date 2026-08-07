package com.nexters.gamss.global.config

import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfig::class)
class SwaggerDocsTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `서비스 그룹 문서에 인증 API와 JWT 보안 스키마가 노출된다`() {
        mockMvc
            .get("/v3/api-docs/1-service")
            .andExpect {
                status { isOk() }
                jsonPath("$.info.title") { value("GAMSS API") }
                jsonPath("$.paths['/api/auth/login'].post") { exists() }
                jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            }
    }

    @Test
    fun `서비스 그룹 문서에는 백오피스 API가 노출되지 않는다`() {
        mockMvc
            .get("/v3/api-docs/1-service")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/admin/members']") { doesNotExist() }
                jsonPath("$.paths['/api/admin/dashboard']") { doesNotExist() }
            }
    }

    @Test
    fun `그룹 드롭다운에서 서비스 문서가 첫 번째로 온다`() {
        // 드롭다운은 표시명 순 정렬이라, 표시명 숫자 접두사가 순서를 고정한다.
        mockMvc
            .get("/v3/api-docs/swagger-config")
            .andExpect {
                status { isOk() }
                jsonPath("$.urls[0].name") { value("1. 서비스 API (앱)") }
                jsonPath("$.urls[1].name") { value("2. 백오피스 API") }
            }
    }

    @Test
    fun `백오피스 그룹 문서에는 어드민 API만 노출된다`() {
        mockMvc
            .get("/v3/api-docs/2-admin")
            .andExpect {
                status { isOk() }
                jsonPath("$.paths['/api/admin/members']") { exists() }
                jsonPath("$.paths['/api/auth/login']") { doesNotExist() }
            }
    }
}
