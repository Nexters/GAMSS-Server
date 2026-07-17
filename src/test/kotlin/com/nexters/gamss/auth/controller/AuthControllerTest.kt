package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.service.AuthFacade
import com.nexters.gamss.auth.service.TokenResult
import com.nexters.gamss.global.exception.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import kotlin.test.Test

class AuthControllerTest {
    private val authFacade = mockk<AuthFacade>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(authFacade))
            .setControllerAdvice(GlobalExceptionHandler())
            .setValidator(LocalValidatorFactoryBean().apply { afterPropertiesSet() })
            .build()

    @Test
    fun `로그인에 성공하면 토큰을 반환한다`() {
        every { authFacade.login(OAuthProvider.GOOGLE, "idtok") } returns TokenResult("access", "refresh")

        mockMvc
            .post("/api/auth/login/google") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":"idtok"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.accessToken") { value("access") }
                jsonPath("$.data.refreshToken") { value("refresh") }
            }
    }

    @Test
    fun `idToken이 비어 있으면 400과 INVALID_INPUT을 반환한다`() {
        mockMvc
            .post("/api/auth/login/google") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `지원하지 않는 provider면 400과 UNSUPPORTED_OAUTH_PROVIDER를 반환한다`() {
        mockMvc
            .post("/api/auth/login/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":"x"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("UNSUPPORTED_OAUTH_PROVIDER") }
            }
    }

    @Test
    fun `재발급에 성공하면 새 토큰을 반환한다`() {
        every { authFacade.reissue("r") } returns TokenResult("na", "nr")

        mockMvc
            .post("/api/auth/reissue") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"refreshToken":"r"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.accessToken") { value("na") }
                jsonPath("$.data.refreshToken") { value("nr") }
            }
    }
}
