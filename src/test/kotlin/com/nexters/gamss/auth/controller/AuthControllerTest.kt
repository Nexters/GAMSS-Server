package com.nexters.gamss.auth.controller

import com.nexters.gamss.auth.service.AuthService
import com.nexters.gamss.auth.service.LoginResult
import com.nexters.gamss.auth.service.TokenResult
import com.nexters.gamss.global.web.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import kotlin.test.Test

class AuthControllerTest {
    private val authService = mockk<AuthService>()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(authService))
            .setControllerAdvice(GlobalExceptionHandler())
            .setValidator(LocalValidatorFactoryBean().apply { afterPropertiesSet() })
            .build()

    @Test
    fun `로그인에 성공하면 토큰과 최초 가입 여부를 반환한다`() {
        every { authService.login("idtok") } returns LoginResult("access", "refresh", isFirstLogin = true)

        mockMvc
            .post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":"idtok"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.accessToken") { value("access") }
                jsonPath("$.data.refreshToken") { value("refresh") }
                jsonPath("$.data.isFirstLogin") { value(true) }
            }
    }

    @Test
    fun `idToken이 비어 있으면 400과 INVALID_INPUT을 반환한다`() {
        mockMvc
            .post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"idToken":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `재발급에 성공하면 새 토큰을 반환한다`() {
        every { authService.reissue("r") } returns TokenResult("na", "nr")

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
