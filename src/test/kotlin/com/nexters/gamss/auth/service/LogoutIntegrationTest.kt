package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.domain.RefreshToken
import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.auth.social.SocialUser
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 로그아웃 검증. 저장된 리프레시 토큰을 폐기해 그 토큰으로는 더 이상 재발급되지 않아야 한다(#71).
 * 경로 보호(로그아웃은 인증 필요)까지 함께 확인한다 — `/api/auth/` 하위를 통째로 공개해두면
 * 인증 없이 호출돼 principal 이 비게 된다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, LogoutIntegrationTest.StubVerifierConfig::class)
@Transactional
class LogoutIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    private lateinit var mockMvc: MockMvc

    @TestConfiguration(proxyBeanMethods = false)
    class StubVerifierConfig {
        @Bean
        @Primary
        fun stubSocialTokenVerifier(): SocialTokenVerifier =
            object : SocialTokenVerifier {
                override fun verify(idToken: String) = SocialUser("logout-uid", SocialProvider.GOOGLE, "logout@a.com", "로그아웃")
            }
    }

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @Test
    fun `로그아웃하면 저장된 리프레시 토큰이 폐기된다`() {
        val issued = authService.login("idtok")
        val memberId = memberRepository.findAll().single().id
        assertNotNull(refreshTokenRepository.findByMemberId(memberId))

        mockMvc
            .post("/api/auth/logout") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(memberId)}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }

        assertNull(refreshTokenRepository.findByMemberId(memberId), "저장된 토큰이 남아 있으면 안 된다")
        val exception = assertFailsWith<BusinessException> { authService.reissue(issued.refreshToken) }
        assertEquals(ErrorCode.REFRESH_TOKEN_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `저장된 토큰이 없어도 로그아웃은 성공한다`() {
        val member = memberRepository.save(Member("no-token@a.com"))

        repeat(2) {
            mockMvc
                .post("/api/auth/logout") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(member.id)}")
                }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `인증 없이 로그아웃을 호출하면 401을 반환한다`() {
        mockMvc
            .post("/api/auth/logout")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `로그아웃은 자기 토큰만 폐기하고 다른 회원 토큰은 건드리지 않는다`() {
        authService.login("idtok")
        val me = memberRepository.findAll().single().id
        val other = memberRepository.save(Member("other@a.com"))
        refreshTokenRepository.save(RefreshToken(other.id, jwtIssuer.issueRefreshToken(other.id)))

        mockMvc
            .post("/api/auth/logout") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(me)}")
            }.andExpect { status { isOk() } }

        assertNull(refreshTokenRepository.findByMemberId(me))
        assertNotNull(refreshTokenRepository.findByMemberId(other.id), "다른 회원 토큰은 남아 있어야 한다")
    }

    @Test
    fun `로그인과 재발급은 여전히 인증 없이 호출할 수 있다`() {
        mockMvc
            .post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":"idtok"}"""
            }.andExpect { status { isOk() } }
    }
}
