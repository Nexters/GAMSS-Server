package com.nexters.gamss.notification.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 같은 토큰이 동시에 등록돼도 모두 성공하고 행이 하나만 남아야 한다.
 *
 * 이 경합이 [com.nexters.gamss.notification.repository.DeviceTokenRepository.upsert] 를 한 문장으로
 * 만든 이유다. 조회-후-저장으로 되돌리면 두 요청이 모두 "없음"을 보고 각자 insert 해 뒤늦은 쪽이
 * 유니크 제약에 걸린다 — **순차로 두 번 부르는 테스트로는 그 회귀가 드러나지 않아** 여기서 잡는다.
 *
 * 앱은 실행할 때마다 같은 토큰을 다시 등록하므로 재시도·연타로 실제 생길 수 있는 경합이다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ConcurrentDeviceTokenRegistrationTest {
    @Autowired
    private lateinit var deviceTokenService: DeviceTokenService

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @AfterEach
    fun cleanUp() {
        // 실제 커밋이 필요해 @Transactional 롤백을 쓸 수 없으므로 직접 정리한다.
        deviceTokenRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `같은 토큰을 동시에 등록해도 모두 성공하고 행은 하나만 남는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        val errors = registerConcurrently { deviceTokenService.register(member.id, FcmToken(TOKEN)) }

        assertTrue(errors.isEmpty(), "동시 등록 중 예외 발생: $errors")
        assertEquals(1, deviceTokenRepository.count(), "같은 기기의 토큰은 하나만 남아야 한다")
        assertEquals(member.id, deviceTokenRepository.findByToken(FcmToken(TOKEN))?.memberId)
    }

    /**
     * 기기를 넘겨받는 순간에도 행이 갈라지지 않아야 한다. 둘 중 누가 마지막인지는 경합 결과라
     * 정해지지 않지만, **행이 하나이고 그 주인이 둘 중 하나**라는 것은 지켜져야 한다.
     */
    @Test
    fun `두 회원이 같은 토큰을 동시에 등록해도 행은 하나만 남는다`() {
        val previous = memberRepository.save(Member("before@a.com"))
        val next = memberRepository.save(Member("after@a.com"))

        val errors =
            registerConcurrently { index ->
                val memberId = if (index % 2 == 0) previous.id else next.id
                deviceTokenService.register(memberId, FcmToken(TOKEN))
            }

        assertTrue(errors.isEmpty(), "동시 등록 중 예외 발생: $errors")
        assertEquals(1, deviceTokenRepository.count())
        val owner = assertNotNull(deviceTokenRepository.findByToken(FcmToken(TOKEN))).memberId
        assertTrue(owner == previous.id || owner == next.id, "소유자는 등록한 두 회원 중 하나여야 한다")
    }

    /** 모든 스레드를 같은 출발선에 세워 경합을 유도하고, 각 스레드에서 난 예외를 모아 돌려준다. */
    private fun registerConcurrently(register: (Int) -> Unit): List<Throwable> {
        val startLine = CyclicBarrier(THREAD_COUNT)
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        val futures =
            (0 until THREAD_COUNT).map { index ->
                executor.submit {
                    try {
                        startLine.await()
                        register(index)
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()
        return errors
    }

    companion object {
        private const val THREAD_COUNT = 8
        private const val TOKEN = "fMEk9dQvS0m1:APA91bH-concurrent"
    }
}
