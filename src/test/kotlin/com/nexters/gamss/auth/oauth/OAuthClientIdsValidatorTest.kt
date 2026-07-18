package com.nexters.gamss.auth.oauth

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class OAuthClientIdsValidatorTest {
    private val logger = LoggerFactory.getLogger(OAuthClientIdsValidator::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun properties(
        google: List<String>,
        apple: List<String>,
    ) = OAuthProperties(
        google = OAuthProperties.Provider("g-iss", "g-jwks", google),
        apple = OAuthProperties.Provider("a-iss", "a-jwks", apple),
    )

    private fun environment(vararg activeProfiles: String): Environment =
        mockk { every { this@mockk.activeProfiles } returns arrayOf(*activeProfiles) }

    private fun warnings(): List<String> = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    @Test
    fun `dev 프로필에서 client-ids가 모두 있으면 경고가 없다`() {
        OAuthClientIdsValidator(properties(listOf("g"), listOf("a")), environment("dev")).afterPropertiesSet()

        assertTrue(warnings().isEmpty())
    }

    @Test
    fun `dev 프로필에서 google client-ids가 비면 경고를 남기고 부팅은 계속된다`() {
        OAuthClientIdsValidator(properties(emptyList(), listOf("a")), environment("dev")).afterPropertiesSet()

        assertTrue(warnings().any { it.contains("GOOGLE_CLIENT_IDS") })
    }

    @Test
    fun `prod 프로필에서 apple client-ids가 비면 경고를 남긴다`() {
        OAuthClientIdsValidator(properties(listOf("g"), emptyList()), environment("prod")).afterPropertiesSet()

        assertTrue(warnings().any { it.contains("APPLE_CLIENT_IDS") })
    }

    @Test
    fun `local 프로필에서는 client-ids가 비어도 경고가 없다`() {
        OAuthClientIdsValidator(properties(emptyList(), emptyList()), environment("local")).afterPropertiesSet()

        assertTrue(warnings().isEmpty())
    }

    @Test
    fun `활성 프로필이 없으면 client-ids가 비어도 경고가 없다`() {
        OAuthClientIdsValidator(properties(emptyList(), emptyList()), environment()).afterPropertiesSet()

        assertTrue(warnings().isEmpty())
    }
}
