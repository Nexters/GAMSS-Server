package com.nexters.gamss.member.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class MemberControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var generationLogRepository: GenerationLogRepository

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
    fun `닉네임을 수정하면 변경된 닉네임을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .patch("/api/members/me/nickname") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname":"바다"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.nickname") { value("바다") }
            }
    }

    @Test
    fun `닉네임이 공백이면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .patch("/api/members/me/nickname") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname":"   "}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `닉네임이 최대 길이를 넘으면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val tooLong = "가".repeat(Nickname.MAX_LENGTH + 1)

        mockMvc
            .patch("/api/members/me/nickname") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname":"$tooLong"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_NICKNAME") }
            }
    }

    @Test
    fun `금칙어가 포함된 닉네임은 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .patch("/api/members/me/nickname") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname":"시발이"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_NICKNAME") }
            }
    }

    @Test
    fun `인증 없이 닉네임을 수정하면 401을 반환한다`() {
        mockMvc
            .patch("/api/members/me/nickname") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname":"바다"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `회원을 탈퇴하면 소프트 삭제된다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .delete("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }

        assertTrue(memberRepository.findById(member.id).get().isWithdrawn())
    }

    @Test
    fun `이미 탈퇴한 회원이 다시 탈퇴하면 409를 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val bearer = bearerFor(member)

        mockMvc.delete("/api/members/me") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect {
            status { isOk() }
        }

        mockMvc
            .delete("/api/members/me") {
                header(HttpHeaders.AUTHORIZATION, bearer)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("ALREADY_WITHDRAWN") }
            }
    }

    @Test
    fun `인증 없이 탈퇴하면 401을 반환한다`() {
        mockMvc
            .delete("/api/members/me")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `오늘 소비한 토큰 합을 조회하고 상한 비활성 환경(dev)에서는 상한이 null로 내려간다`() {
        val member = memberRepository.save(Member("me@a.com"))
        generationLogRepository.save(generationLog(member.id, usedTokens = 100))
        generationLogRepository.save(generationLog(member.id, usedTokens = 200))

        mockMvc
            .get("/api/members/me/token-usage") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.usedTokens") { value(300) }
                jsonPath("$.data.dailyLimit", nullValue())
                jsonPath("$.data.exceeded") { value(false) }
            }
    }

    @Test
    fun `다른 회원의 생성 로그는 내 토큰 사용량에 합산되지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        generationLogRepository.save(generationLog(member.id, usedTokens = 100))
        generationLogRepository.save(generationLog(other.id, usedTokens = 900))

        mockMvc
            .get("/api/members/me/token-usage") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.usedTokens") { value(100) }
            }
    }

    @Test
    fun `인증 없이 토큰 사용량을 조회하면 401을 반환한다`() {
        mockMvc
            .get("/api/members/me/token-usage")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    private fun generationLog(
        memberId: Long,
        usedTokens: Int,
    ): GenerationLog =
        GenerationLog(
            generationType = GenerationType.COMMENT,
            model = "test-model",
            memberId = memberId,
            success = true,
            attemptCount = 1,
            usedTokens = usedTokens,
            latencyMs = 100,
            createdAt = Instant.now(),
        )

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"
}
