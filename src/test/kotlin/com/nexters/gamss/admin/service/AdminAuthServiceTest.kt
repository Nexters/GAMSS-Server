package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.config.AdminProperties
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.auth.social.SocialUser
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAuthServiceTest {
    private val socialTokenVerifier = mockk<SocialTokenVerifier>()
    private val jwtIssuer = mockk<JwtIssuer>()

    private fun service(vararg emails: String) = AdminAuthService(socialTokenVerifier, AdminProperties(emails.toList()), jwtIssuer)

    @Test
    fun `허용목록에 있는 이메일이면 관리자 토큰을 발급한다`() {
        every { socialTokenVerifier.verify("idtok") } returns
            SocialUser("uid-1", SocialProvider.GOOGLE, "Admin@Gamss.KR")
        every { jwtIssuer.issueAdminToken("admin@gamss.kr") } returns "admin-token"

        val token = service("admin@gamss.kr").login("idtok")

        assertEquals("admin-token", token)
    }

    @Test
    fun `허용목록에 없는 이메일이면 NOT_ADMIN 예외를 던진다`() {
        every { socialTokenVerifier.verify("idtok") } returns
            SocialUser("uid-1", SocialProvider.GOOGLE, "intruder@evil.com")

        val exception = assertFailsWith<BusinessException> { service("admin@gamss.kr").login("idtok") }

        assertEquals(ErrorCode.NOT_ADMIN, exception.errorCode)
    }

    @Test
    fun `구글이 아닌 제공자면 허용목록에 있어도 NOT_ADMIN 예외를 던진다`() {
        every { socialTokenVerifier.verify("idtok") } returns
            SocialUser("uid-1", SocialProvider.APPLE, "admin@gamss.kr")

        val exception = assertFailsWith<BusinessException> { service("admin@gamss.kr").login("idtok") }

        assertEquals(ErrorCode.NOT_ADMIN, exception.errorCode)
    }

    @Test
    fun `토큰에 이메일이 없으면 NOT_ADMIN 예외를 던진다`() {
        every { socialTokenVerifier.verify("idtok") } returns
            SocialUser("uid-1", SocialProvider.GOOGLE, null)

        val exception = assertFailsWith<BusinessException> { service("admin@gamss.kr").login("idtok") }

        assertEquals(ErrorCode.NOT_ADMIN, exception.errorCode)
    }
}
