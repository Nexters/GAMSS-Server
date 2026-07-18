package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthServiceTest {
    private val loginService = mockk<LoginService>()

    // 실제 재시도 정책을 그대로 사용해 위임이 올바른지 검증한다.
    private val authService = AuthService(loginService, ConflictRetry())

    @Test
    fun `로그인은 LoginService에 위임하고 정상이면 재시도하지 않는다`() {
        every { loginService.login("token") } returns TokenResult("a", "r")

        val result = authService.login("token")

        assertEquals("a", result.accessToken)
        verify(exactly = 1) { loginService.login("token") }
    }

    @Test
    fun `동시 가입 경합이 나면 재시도해 성공한다`() {
        every { loginService.login("token") } throws
            ConcurrentRegistrationException(OAuthProvider.GOOGLE, "uid") andThen TokenResult("a", "r")

        val result = authService.login("token")

        assertEquals("a", result.accessToken)
        verify(exactly = 2) { loginService.login("token") }
    }

    @Test
    fun `재발급은 재시도 없이 LoginService에 위임한다`() {
        every { loginService.reissue("r") } returns TokenResult("na", "nr")

        val result = authService.reissue("r")

        assertEquals("na", result.accessToken)
        verify(exactly = 1) { loginService.reissue("r") }
    }

    @Test
    fun `재발급 중 예외는 그대로 전파한다`() {
        every { loginService.reissue("bad") } throws IllegalStateException("boom")

        assertFailsWith<IllegalStateException> { authService.reissue("bad") }
    }
}
