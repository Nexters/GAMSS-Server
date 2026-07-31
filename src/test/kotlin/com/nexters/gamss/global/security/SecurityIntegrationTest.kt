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
import java.time.Duration

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

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    private lateinit var mockMvc: MockMvc

    /** 서명은 앱과 같은 키로 하되 유효기간만 음수라, 발급 즉시 만료된 토큰을 만든다. */
    private lateinit var expiredIssuer: JwtIssuer

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
        expiredIssuer =
            JjwtIssuer(
                jwtProperties.copy(
                    accessTokenValidity = Duration.ofSeconds(-1),
                    refreshTokenValidity = Duration.ofSeconds(-1),
                ),
            )
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
    fun `만료된 액세스 토큰이면 401과 EXPIRED_TOKEN을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val expiredToken = expiredIssuer.issueAccessToken(member.id)

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("EXPIRED_TOKEN") }
            }
    }

    @Test
    fun `만료된 관리자 토큰이면 401과 EXPIRED_TOKEN을 반환한다`() {
        val expiredToken = expiredIssuer.issueAdminToken("admin@gamss.kr")

        mockMvc
            .get("/api/admin/members") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("EXPIRED_TOKEN") }
            }
    }

    @Test
    fun `만료된 리프레시 토큰으로 보호된 자원에 접근하면 EXPIRED_TOKEN을 반환한다`() {
        // 만료는 토큰 종류 검사보다 먼저 판정된다 — 종류가 무엇이든 만료면 EXPIRED_TOKEN 이다.
        val member = memberRepository.save(Member("me@a.com"))
        val expiredRefreshToken = expiredIssuer.issueRefreshToken(member.id)

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $expiredRefreshToken")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("EXPIRED_TOKEN") }
            }
    }

    @Test
    fun `다른 키로 서명된 토큰은 만료가 아니므로 UNAUTHORIZED를 반환한다`() {
        val forgedIssuer =
            JjwtIssuer(jwtProperties.copy(secret = jwtProperties.secret.reversed()))
        val forgedToken = forgedIssuer.issueAccessToken(1L)

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $forgedToken")
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `만료 응답은 다음 요청에 남지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${expiredIssuer.issueAccessToken(member.id)}")
            }.andExpect { jsonPath("$.error.code") { value("EXPIRED_TOKEN") } }

        // 요청 속성으로 사유를 넘기므로 요청 간에 상태가 새지 않아야 한다.
        mockMvc
            .get("/api/members/me")
            .andExpect {
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
