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
import org.springframework.test.web.servlet.delete
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
    fun `리프레시 토큰으로는 보호된 자원에 접근할 수 없다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val refreshToken = jwtIssuer.issueRefreshToken(member.id)

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `탈퇴한 회원의 리프레시 토큰으로도 조회할 수 없다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val refreshToken = jwtIssuer.issueRefreshToken(member.id)

        mockMvc
            .delete("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(member.id)}")
            }.andExpect { status { isOk() } }

        // refresh 유효기간(14일)이 access(1시간)보다 길어, 여기서 통과하면
        // 탈퇴 후 잔존 기간이 의도한 1시간에서 14일로 벌어진다.
        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            }.andExpect {
                status { isUnauthorized() }
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

    @Test
    fun `ROLE_ADMIN이 없는 회원 토큰으로 admin API를 호출하면 403과 ACCESS_DENIED를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val token = jwtIssuer.issueAccessToken(member.id)

        mockMvc
            .get("/api/admin/members") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("ACCESS_DENIED") }
            }
    }

    @Test
    fun `admin 토큰으로 통계 days 상한을 넘기면 400을 반환한다`() {
        val token = jwtIssuer.issueAdminToken("admin@gamss.kr")

        mockMvc
            .get("/api/admin/members/stats") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("days", "1000000")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }
}
