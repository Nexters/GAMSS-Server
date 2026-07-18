package com.nexters.gamss.auth.social

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SocialProviderTest {
    @Test
    fun `Firebase sign_in_provider를 provider로 매핑한다`() {
        assertEquals(SocialProvider.GOOGLE, SocialProvider.fromFirebase("google.com"))
        assertEquals(SocialProvider.APPLE, SocialProvider.fromFirebase("apple.com"))
    }

    @Test
    fun `지원하지 않는 sign_in_provider면 UNSUPPORTED_SOCIAL_PROVIDER`() {
        val exception = assertFailsWith<BusinessException> { SocialProvider.fromFirebase("password") }

        assertEquals(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER, exception.errorCode)
    }

    @Test
    fun `sign_in_provider가 없으면 UNSUPPORTED_SOCIAL_PROVIDER`() {
        val exception = assertFailsWith<BusinessException> { SocialProvider.fromFirebase(null) }

        assertEquals(ErrorCode.UNSUPPORTED_SOCIAL_PROVIDER, exception.errorCode)
    }
}
