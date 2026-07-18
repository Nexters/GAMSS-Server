package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthProviderTest {
    @Test
    fun `대소문자 무관하게 provider를 파싱한다`() {
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.from("google"))
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.from("GOOGLE"))
        assertEquals(OAuthProvider.APPLE, OAuthProvider.from("Apple"))
    }

    @Test
    fun `지원하지 않는 provider면 UNSUPPORTED_OAUTH_PROVIDER`() {
        val exception = assertFailsWith<BusinessException> { OAuthProvider.from("kakao") }

        assertEquals(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.errorCode)
    }
}
