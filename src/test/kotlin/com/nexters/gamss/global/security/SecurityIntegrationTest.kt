package com.nexters.gamss.global.security

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class SecurityIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

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
    fun `유효한 토큰이면 보호된 자원에 접근할 수 있다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val token = jwtIssuer.issueAccessToken(member.id)

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(member.id) }
                jsonPath("$.data.email") { value("me@a.com") }
                jsonPath("$.data.status") { value("ACTIVE") }
                jsonPath("$.data.createdAt") { exists() }
            }
    }

    @Test
    fun `토큰이 없으면 401과 UNAUTHORIZED를 반환한다`() {
        mockMvc
            .get("/api/members/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `잘못된 토큰이면 401을 반환한다`() {
        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `Bearer 형식이 아닌 헤더는 인증되지 않아 401을 반환한다`() {
        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }
}
