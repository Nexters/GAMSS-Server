package com.nexters.gamss.auth.oauth

import io.mockk.every
import io.mockk.mockk
import org.springframework.core.env.Environment
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OAuthClientIdsValidatorTest {
    private fun properties(
        google: List<String>,
        apple: List<String>,
    ) = OAuthProperties(
        google = OAuthProperties.Provider("g-iss", "g-jwks", google),
        apple = OAuthProperties.Provider("a-iss", "a-jwks", apple),
    )

    private fun environment(vararg activeProfiles: String): Environment =
        mockk { every { this@mockk.activeProfiles } returns arrayOf(*activeProfiles) }

    @Test
    fun `dev 프로필에서 client-ids가 모두 있으면 통과한다`() {
        val validator = OAuthClientIdsValidator(properties(listOf("g"), listOf("a")), environment("dev"))

        validator.afterPropertiesSet()
    }

    @Test
    fun `dev 프로필에서 google client-ids가 비면 기동에 실패한다`() {
        val validator = OAuthClientIdsValidator(properties(emptyList(), listOf("a")), environment("dev"))

        assertFailsWith<IllegalArgumentException> { validator.afterPropertiesSet() }
    }

    @Test
    fun `prod 프로필에서 apple client-ids가 비면 기동에 실패한다`() {
        val validator = OAuthClientIdsValidator(properties(listOf("g"), emptyList()), environment("prod"))

        assertFailsWith<IllegalArgumentException> { validator.afterPropertiesSet() }
    }

    @Test
    fun `local 프로필에서는 client-ids가 비어도 통과한다`() {
        val validator = OAuthClientIdsValidator(properties(emptyList(), emptyList()), environment("local"))

        validator.afterPropertiesSet()
    }

    @Test
    fun `활성 프로필이 없으면 client-ids가 비어도 통과한다`() {
        val validator = OAuthClientIdsValidator(properties(emptyList(), emptyList()), environment())

        validator.afterPropertiesSet()
    }
}
