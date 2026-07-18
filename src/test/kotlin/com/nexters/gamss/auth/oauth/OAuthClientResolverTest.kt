package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class OAuthClientResolverTest {
    @Test
    fun `provider에 맞는 클라이언트를 반환한다`() {
        val googleClient = fakeClient(OAuthProvider.GOOGLE)
        val resolver = OAuthClientResolver(listOf(googleClient, fakeClient(OAuthProvider.APPLE)))

        assertSame(googleClient, resolver.resolve(OAuthProvider.GOOGLE))
    }

    @Test
    fun `등록되지 않은 provider면 UNSUPPORTED_OAUTH_PROVIDER`() {
        val resolver = OAuthClientResolver(listOf(fakeClient(OAuthProvider.GOOGLE)))

        val exception = assertFailsWith<BusinessException> { resolver.resolve(OAuthProvider.APPLE) }

        assertEquals(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.errorCode)
    }

    private fun fakeClient(target: OAuthProvider) =
        object : OAuthClient {
            override val provider = target

            override fun verify(idToken: String) = OAuthUserInfo("id", null)
        }
}
