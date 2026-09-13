package com.nexters.gamss.llm.generation

import com.google.genai.errors.ClientException
import com.google.genai.errors.ServerException
import com.nexters.gamss.llm.provider.LlmProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 서킷의 상태 전이를 실제 시계 없이 검증한다. [GeminiCircuitPolicy.config]가 시계를 받는 것이 이
 * 테스트를 위해서다 — 안 그러면 half-open 회복 하나를 보려고 30초를 자야 한다.
 */
class GeminiCircuitBreakersTest {
    private val clock = MutableClock(Instant.parse("2026-09-10T05:00:00Z"))
    private val breakers = GeminiCircuitBreakers(clock)
    private val breaker = breakers.of(LlmProvider.AI_STUDIO)

    @Test
    fun `기동 직후 경로마다 서킷이 CLOSED 로 등록돼 있다`() {
        // 지연 생성하면 첫 호출 전까지 상태 메트릭이 없어, 대시보드가 첫 장애 전까지 빈 그래프가 된다.
        assertEquals(LlmProvider.entries.size, breakers.registry.allCircuitBreakers.size)
        LlmProvider.entries.forEach {
            assertEquals(CircuitBreaker.State.CLOSED, breakers.of(it).state, "provider=$it")
        }
    }

    @Test
    fun `실패율이 임계에 닿으면 서킷이 열린다`() {
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) { breaker.fail(ServerException(503, "UNAVAILABLE", "overloaded")) }

        assertEquals(CircuitBreaker.State.OPEN, breaker.state)
    }

    @Test
    fun `최소 호출 수를 채우기 전에는 열지 않는다`() {
        // 초기 한두 건의 실패로 열리면 멀쩡한 업스트림을 우리가 끊는 셈이 된다.
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS - 1) { breaker.fail(ServerException(503, "UNAVAILABLE", "overloaded")) }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
    }

    @Test
    fun `서킷이 열려 있으면 호출이 아예 나가지 않는다`() {
        openCircuit()
        var called = false

        assertFailsWith<CallNotPermittedException> {
            breaker.executeSupplier { called = true }
        }

        // 이것이 이 작업의 실제 효과다. 호출이 안 나가므로 타임아웃을 기다릴 일도, 스레드를 붙잡을 일도 없다.
        assertTrue(!called)
    }

    @Test
    fun `대기 시간이 지나면 탐침을 통과시키고 성공하면 닫힌다`() {
        openCircuit()

        clock.advance(GeminiCircuitPolicy.WAIT_DURATION_IN_OPEN_STATE.plusSeconds(1))

        // 탐침으로 통과하는 것은 합성 호출이 아니라 실제 사용자 요청이다. 아직 회복 전이면 그 요청들이
        // 풀 타임아웃을 먹는다 — 그 대신 토큰을 쓰지 않는다.
        repeat(GeminiCircuitPolicy.PERMITTED_CALLS_IN_HALF_OPEN_STATE) {
            breaker.executeSupplier { "성공" }
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
    }

    @Test
    fun `탐침이 다시 실패하면 도로 열린다`() {
        openCircuit()
        clock.advance(GeminiCircuitPolicy.WAIT_DURATION_IN_OPEN_STATE.plusSeconds(1))

        repeat(GeminiCircuitPolicy.PERMITTED_CALLS_IN_HALF_OPEN_STATE) {
            breaker.fail(ServerException(503, "UNAVAILABLE", "overloaded"))
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.state)
    }

    @Test
    fun `429는 가용성 실패로 세어 서킷을 연다`() {
        // 우리가 너무 빨리 부르는 것이지만, 계속 부른다고 풀리지 않는 것은 같다.
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) { breaker.fail(ClientException(429, "RESOURCE_EXHAUSTED", "quota")) }

        assertEquals(CircuitBreaker.State.OPEN, breaker.state)
    }

    @Test
    fun `설정 오류는 아무리 나도 서킷을 열지 않는다`() {
        // 400·401·403은 서킷이 열려봐야 고쳐지지 않는다. 여기서 세면 가용성 지표만 오염된다.
        repeat(GeminiCircuitPolicy.SLIDING_WINDOW_SIZE * 2) { breaker.fail(ClientException(400, "INVALID_ARGUMENT", "bad request")) }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
    }

    @Test
    fun `탐침이 설정 오류만 만나면 회복으로 치지 않는다`() {
        // 400 을 "실패가 아닌 것"으로만 두면 라이브러리가 성공으로 세고, 탐침 세 번이 전부 설정 오류일 때
        // 회복한 적이 없는데 서킷이 닫힌다. 그러면 죽은 업스트림으로 트래픽이 도로 쏟아진다.
        openCircuit()
        clock.advance(GeminiCircuitPolicy.WAIT_DURATION_IN_OPEN_STATE.plusSeconds(1))

        repeat(GeminiCircuitPolicy.PERMITTED_CALLS_IN_HALF_OPEN_STATE) {
            breaker.fail(ClientException(400, "INVALID_ARGUMENT", "bad request"))
        }

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state)
    }

    @Test
    fun `한 경로가 열려도 다른 경로는 그대로 쓴다`() {
        openCircuit()

        // 백오피스에서 경로를 바꾸는 것이 곧 복구 수단이다. 공통 서킷 하나였다면 여기가 막혀 있다.
        assertEquals(CircuitBreaker.State.CLOSED, breakers.of(LlmProvider.VERTEX_AI).state)
        assertEquals("살아있음", breakers.of(LlmProvider.VERTEX_AI).executeSupplier { "살아있음" })
    }

    private fun openCircuit() {
        repeat(GeminiCircuitPolicy.MINIMUM_NUMBER_OF_CALLS) { breaker.fail(ServerException(503, "UNAVAILABLE", "overloaded")) }
        check(breaker.state == CircuitBreaker.State.OPEN) { "서킷이 열리지 않았습니다: ${breaker.state}" }
    }

    private fun CircuitBreaker.fail(error: RuntimeException) {
        runCatching { executeSupplier { throw error } }
    }
}

/** 흐르지 않고 밀어야만 가는 시계. 서킷의 대기 시간을 실제로 자지 않고 넘기는 데 쓴다. */
private class MutableClock(
    private var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
