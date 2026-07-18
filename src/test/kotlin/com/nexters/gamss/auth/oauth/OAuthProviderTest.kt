package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthProviderTest {
    @Test
    fun `Firebase sign_in_provider를 provider로 매핑한다`() {
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.fromFirebase("google.com"))
        assertEquals(OAuthProvider.APPLE, OAuthProvider.fromFirebase("apple.com"))
    }

    @Test
    fun `지원하지 않는 sign_in_provider면 UNSUPPORTED_OAUTH_PROVIDER`() {
        val exception = assertFailsWith<BusinessException> { OAuthProvider.fromFirebase("password") }

        assertEquals(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.errorCode)
    }

    @Test
    fun `sign_in_provider가 없으면 UNSUPPORTED_OAUTH_PROVIDER`() {
        val exception = assertFailsWith<BusinessException> { OAuthProvider.fromFirebase(null) }

        assertEquals(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.errorCode)
    }
}
