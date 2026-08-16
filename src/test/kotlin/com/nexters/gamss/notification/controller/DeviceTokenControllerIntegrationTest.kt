package com.nexters.gamss.notification.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.repository.DeviceTokenRepository
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class DeviceTokenControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

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
    fun `디바이스 토큰을 등록한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$TOKEN"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
            }

        val saved = deviceTokenRepository.findByToken(FcmToken(TOKEN))
        assertNotNull(saved)
        assertEquals(member.id, saved.memberId)
    }

    @Test
    fun `앱 실행마다 같은 토큰을 등록해도 성공한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val bearer = bearerFor(member)

        repeat(2) {
            mockMvc
                .post(PATH) {
                    header(HttpHeaders.AUTHORIZATION, bearer)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"token":"$TOKEN"}"""
                }.andExpect {
                    status { isOk() }
                }
        }

        assertEquals(1, deviceTokenRepository.findAll().count { it.token == FcmToken(TOKEN) })
    }

    @Test
    fun `등록한 토큰을 해제한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val bearer = bearerFor(member)
        mockMvc.post(PATH) {
            header(HttpHeaders.AUTHORIZATION, bearer)
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"$TOKEN"}"""
        }

        mockMvc
            .delete(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$TOKEN"}"""
            }.andExpect {
                status { isOk() }
            }

        assertNull(deviceTokenRepository.findByToken(FcmToken(TOKEN)))
    }

    @Test
    fun `등록한 적 없는 토큰을 해제해도 성공한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .delete(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"never-registered"}"""
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `token이 없으면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `토큰이 컬럼 크기를 넘으면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val tooLong = "a".repeat(FcmToken.COLUMN_LENGTH + 1)

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$tooLong"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_DEVICE_TOKEN") }
            }
    }

    @Test
    fun `인증 없이 등록하면 401을 반환한다`() {
        mockMvc
            .post(PATH) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$TOKEN"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `인증 없이 해제하면 401을 반환한다`() {
        mockMvc
            .delete(PATH) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"token":"$TOKEN"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    companion object {
        private const val PATH = "/api/members/me/device-tokens"
        private const val TOKEN = "fMEk9dQvS0m1:APA91bH-device-token"
    }
}
