package com.nexters.gamss.member.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthProviderTest {
    @Test
    fun `대소문자 무관하게 문자열로 provider를 찾는다`() {
        assertEquals(OAuthProvider.GOOGLE, OAuthProvider.from("google"))
        assertEquals(OAuthProvider.APPLE, OAuthProvider.from("APPLE"))
    }

    @Test
    fun `지원하지 않는 provider면 예외를 던진다`() {
        val exception = assertFailsWith<BusinessException> { OAuthProvider.from("kakao") }

        assertEquals(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.errorCode)
    }
}
