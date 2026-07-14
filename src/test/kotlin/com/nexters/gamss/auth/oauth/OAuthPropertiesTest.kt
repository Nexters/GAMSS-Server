package com.nexters.gamss.auth.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OAuthPropertiesTest {
    @Test
    fun `clientIds를 지정하지 않으면 빈 목록이 기본값이다`() {
        val provider =
            OAuthProperties.Provider(
                issuer = "https://accounts.google.com",
                jwksUri = "https://www.googleapis.com/oauth2/v3/certs",
            )

        assertTrue(provider.clientIds.isEmpty())
    }

    @Test
    fun `google과 apple 설정을 보관한다`() {
        val google = OAuthProperties.Provider(issuer = "g-iss", jwksUri = "g-jwks", clientIds = listOf("g"))
        val apple = OAuthProperties.Provider(issuer = "a-iss", jwksUri = "a-jwks", clientIds = listOf("a"))
        val properties = OAuthProperties(google = google, apple = apple)

        assertEquals(google, properties.google)
        assertEquals(apple, properties.apple)
    }
}
