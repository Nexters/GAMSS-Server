package com.nexters.gamss.llm.generation

import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 서킷 상태를 `/actuator/prometheus` 에 싣는다. 모니터링 서버가 15초마다 긁어간다.
 *
 * 서킷이 여닫히는 것은 로그를 뒤지지 않으면 보이지 않는다. `resilience4j_circuitbreaker_state` 는
 * 경로별(`name` 라벨)로 지금 상태를 그대로 보여주므로, 장애 때 "Gemini 가 죽어서 우리가 끊은 것"과
 * "우리 쪽이 느린 것"을 가르는 첫 지표가 된다.
 *
 * 수동으로 확인할 때는 `tomcat_threads_busy` 를 함께 볼 것. 서킷이 열리는 순간 busy 스레드가
 * 떨어지는 것이 이 작업의 실제 효과다.
 *
 * 레지스트리를 통째로 넘기므로 나중에 경로가 늘어도 여기는 손대지 않는다 - 바인딩이 등록 이벤트를
 * 구독해 새로 생긴 서킷도 따라온다.
 */
@Component
class GeminiCircuitBreakerMetrics(
    registry: MeterRegistry,
    breakers: GeminiCircuitBreakers,
) {
    init {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers.registry).bindTo(registry)
    }
}
