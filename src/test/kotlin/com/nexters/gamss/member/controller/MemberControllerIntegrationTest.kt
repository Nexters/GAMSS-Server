package com.nexters.gamss.member.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

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

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

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
                jsonPath("$.error.code") { value("INVALID_INPUT") }
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
}
