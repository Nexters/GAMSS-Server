package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.provider.LlmProvider
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Gemini 호출 경로마다 서킷을 하나씩 들고 있다.
 *
 * **경로별로 나누는 이유**는 백오피스에서 AI Studio ↔ Vertex AI 를 런타임에 전환하기 때문이다(#215).
 * 공통 하나로 두면 AI Studio 가 죽어 서킷이 열린 뒤 Vertex 로 전환해도
 * [GeminiCircuitPolicy.WAIT_DURATION_IN_OPEN_STATE] 동안 그대로 막힌다 — 전환이 곧 복구 수단인데
 * 서킷이 그걸 못 따라간다. 나눠 두면 쉬고 있던 쪽이 CLOSED 로 시작한다.
 *
 * 상태는 프로세스 안에만 있다. 지금 prod 는 app 인스턴스가 하나라 문제없지만, 스케일아웃하면
 * 인스턴스마다 따로 학습한다.
 */
@Component
class GeminiCircuitBreakers(
    clock: Clock = Clock.systemUTC(),
) {
    /** 메트릭 바인딩이 이 레지스트리를 통째로 구독한다([GeminiCircuitBreakerMetrics]). */
    val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(GeminiCircuitPolicy.config(clock))

    init {
        // 기동 시점에 미리 만들어 둔다. 지연 생성하면 첫 호출 전까지 레지스트리가 비어서 두 경로의
        // 상태 메트릭이 아예 없고, 대시보드가 첫 장애가 나기 전까지 빈 그래프를 보여준다.
        LlmProvider.entries.forEach { of(it) }
    }

    fun of(provider: LlmProvider): CircuitBreaker = registry.circuitBreaker(nameOf(provider))

    /** 메트릭의 `name` 라벨이 되는 값이라 대시보드에서 읽을 모양으로 만든다. AI_STUDIO -> gemini-ai-studio */
    private fun nameOf(provider: LlmProvider): String = "gemini-" + provider.name.lowercase().replace('_', '-')
}
