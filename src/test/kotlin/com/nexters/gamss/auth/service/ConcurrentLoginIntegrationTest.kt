package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthClient
import com.nexters.gamss.auth.oauth.OAuthClientResolver
import com.nexters.gamss.auth.oauth.OAuthProvider
import com.nexters.gamss.auth.oauth.OAuthUserInfo
import com.nexters.gamss.auth.repository.RefreshTokenRepository
import com.nexters.gamss.auth.repository.SocialAccountRepository
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 동시 최초 로그인 경합 검증. 여러 스레드가 같은 소셜 계정으로 동시에 처음 로그인해도
 * 모두 성공하고, 회원·소셜 계정·리프레시 토큰이 각각 하나만 남아야 한다.
 *
 * 재시도가 없으면 뒤늦은 요청이 (provider, providerId) 유니크 제약 위반으로 실패한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class, ConcurrentLoginIntegrationTest.StubOAuthConfig::class)
class ConcurrentLoginIntegrationTest {
    @Autowired
    private lateinit var authFacade: AuthFacade

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @TestConfiguration(proxyBeanMethods = false)
    class StubOAuthConfig {
        // 어떤 idToken 이든 같은 소셜 신원을 반환해, 동시 요청이 같은 계정으로 경합하게 한다.
        @Bean
        @Primary
        fun stubOAuthClientResolver(): OAuthClientResolver =
            OAuthClientResolver(
                listOf(
                    object : OAuthClient {
                        override val provider = OAuthProvider.GOOGLE

                        override fun verify(idToken: String) = OAuthUserInfo("concurrent-sub", "u@a.com")
                    },
                ),
            )
    }

    @AfterEach
    fun cleanUp() {
        // 이 테스트는 실제 커밋이 필요해 @Transactional 롤백을 쓸 수 없으므로 직접 정리한다.
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `첫 로그인이 동시에 여러 개 와도 모두 성공하고 회원은 하나만 생성된다`() {
        val threadCount = 8
        val startLine = CyclicBarrier(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        val futures =
            (1..threadCount).map {
                executor.submit {
                    try {
                        startLine.await() // 모든 스레드를 동시에 출발시켜 경합을 유도한다
                        authFacade.login(OAuthProvider.GOOGLE, "idtok")
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertTrue(errors.isEmpty(), "동시 로그인 중 예외 발생: $errors")
        assertEquals(1, memberRepository.count(), "회원은 하나만 생성돼야 한다")
        assertEquals(1, socialAccountRepository.count(), "소셜 계정은 하나만 생성돼야 한다")
        assertEquals(1, refreshTokenRepository.count(), "리프레시 토큰은 하나만 남아야 한다")
    }
}
