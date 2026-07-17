package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthFacadeTest {
    private val authService = mockk<AuthService>()
    private val authFacade = AuthFacade(authService)

    @Test
    fun `로그인이 정상이면 그대로 반환하고 재시도하지 않는다`() {
        every { authService.login(OAuthProvider.GOOGLE, "t") } returns TokenResult("a", "r")

        val result = authFacade.login(OAuthProvider.GOOGLE, "t")

        assertEquals("a", result.accessToken)
        verify(exactly = 1) { authService.login(OAuthProvider.GOOGLE, "t") }
    }

    @Test
    fun `유니크 충돌이 나면 한 번 재시도해 성공한다`() {
        every { authService.login(OAuthProvider.GOOGLE, "t") } throws
            DataIntegrityViolationException("duplicate") andThen TokenResult("a", "r")

        val result = authFacade.login(OAuthProvider.GOOGLE, "t")

        assertEquals("a", result.accessToken)
        verify(exactly = 2) { authService.login(OAuthProvider.GOOGLE, "t") }
    }

    @Test
    fun `재시도도 충돌하면 예외를 전파한다`() {
        every { authService.login(OAuthProvider.GOOGLE, "t") } throws DataIntegrityViolationException("duplicate")

        assertFailsWith<DataIntegrityViolationException> { authFacade.login(OAuthProvider.GOOGLE, "t") }
        verify(exactly = 2) { authService.login(OAuthProvider.GOOGLE, "t") }
    }

    @Test
    fun `재발급은 재시도 없이 그대로 위임한다`() {
        every { authService.reissue("r") } returns TokenResult("na", "nr")

        val result = authFacade.reissue("r")

        assertEquals("na", result.accessToken)
        verify(exactly = 1) { authService.reissue("r") }
    }
}
