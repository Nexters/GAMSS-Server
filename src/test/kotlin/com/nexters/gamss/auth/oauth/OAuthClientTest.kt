package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.OAuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthClientTest {
    private val properties =
        OAuthProperties(
            google =
                OAuthProperties.Provider(
                    issuer = "https://accounts.google.com",
                    jwksUri = "https://www.googleapis.com/oauth2/v3/certs",
                    clientIds = listOf("google-client"),
                ),
            apple =
                OAuthProperties.Provider(
                    issuer = "https://appleid.apple.com",
                    jwksUri = "https://appleid.apple.com/auth/keys",
                    clientIds = listOf("apple-client"),
                ),
        )

    @Test
    fun `Google 클라이언트는 GOOGLE provider를 가진다`() {
        assertEquals(OAuthProvider.GOOGLE, GoogleOAuthClient(properties).provider)
    }

    @Test
    fun `Apple 클라이언트는 APPLE provider를 가진다`() {
        assertEquals(OAuthProvider.APPLE, AppleOAuthClient(properties).provider)
    }

    @Test
    fun `Google 클라이언트는 잘못된 토큰에 INVALID_SOCIAL_TOKEN`() {
        val exception = assertFailsWith<BusinessException> { GoogleOAuthClient(properties).verify("garbage") }

        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, exception.errorCode)
    }

    @Test
    fun `Apple 클라이언트는 잘못된 토큰에 INVALID_SOCIAL_TOKEN`() {
        val exception = assertFailsWith<BusinessException> { AppleOAuthClient(properties).verify("garbage") }

        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, exception.errorCode)
    }
}
