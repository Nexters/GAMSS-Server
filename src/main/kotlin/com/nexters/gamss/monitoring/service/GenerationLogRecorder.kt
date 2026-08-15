package com.nexters.gamss.monitoring.service

import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * LLM 생성 1요청을 관측 로그로 남긴다(SRP: 기록만 담당). 모델은 앱 전체 단일값이라 여기서 스탬프한다.
 * 기록 실패가 생성 흐름을 절대 깨지 않도록 예외를 삼킨다 — 모니터링 로그는 부가 기능이기 때문이다.
 *
 * 같은 사실을 DB(generation_log)와 메트릭 두 곳에 남기지만 쓰임이 다르다. DB 는 백오피스의 일별
 * 집계·비용 정산처럼 **행 단위로 되짚어야 하는** 용도이고, 메트릭은 초·분 단위 처리량과 지연 분포를
 * 인프라 지표(스레드·커넥션)와 같은 시간축에서 보기 위한 것이다. 그래서 메트릭에는 토큰·비용처럼
 * 백오피스가 이미 정확히 다루는 값을 싣지 않는다.
 */
@Component
class GenerationLogRecorder(
    private val generationLogRepository: GenerationLogRepository,
    private val llmSettingsService: LlmSettingsService,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(
        type: GenerationType,
        success: Boolean,
        attemptCount: Int,
        latencyMs: Long,
        memberId: Long? = null,
        conversationId: Long? = null,
        usedTokens: Int? = null,
        cachedTokens: Int? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        failureReason: String? = null,
    ) {
        // 메트릭을 DB 저장보다 먼저 올린다 — DB 가 흔들려 로그 저장이 실패하는 상황이야말로
        // 관측이 가장 필요한 순간이라, 저장 성공 여부에 메트릭을 묶지 않는다.
        recordMetric(type, success, latencyMs)

        runCatching {
            generationLogRepository.save(
                GenerationLog(
                    generationType = type,
                    model = llmSettingsService.currentModel(),
                    memberId = memberId,
                    conversationId = conversationId,
                    success = success,
                    attemptCount = attemptCount,
                    usedTokens = usedTokens,
                    cachedTokens = cachedTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    latencyMs = latencyMs,
                    failureReason = failureReason?.take(MAX_REASON_LENGTH),
                    createdAt = Instant.now(),
                ),
            )
        }.onFailure { log.warn("생성 로그 기록 실패(무시) type={} success={}", type, success, it) }
    }

    /**
     * 생성 1건의 지연을 타이머로 올린다. 라벨은 [type]·성공 여부 두 개로 묶는다 — 실패 사유나
     * 회원 ID 처럼 값이 계속 늘어나는 라벨을 붙이면 시계열이 폭증해 Prometheus 가 먼저 죽는다.
     *
     * 이 타이머 하나로 처리량(rate)·실패율·지연 분위수를 얻고, 초당 누적 시간(sum 의 rate)은 곧
     * 평균 동시 실행 수라서 LLM 호출이 요청 스레드를 몇 개나 붙잡고 있는지도 계산할 수 있다.
     */
    private fun recordMetric(
        type: GenerationType,
        success: Boolean,
        latencyMs: Long,
    ) {
        runCatching {
            Timer
                .builder("gamss.llm.generation")
                .description("LLM 생성 1건의 소요 시간(재시도 포함)")
                .tag("type", type.name)
                .tag("success", success.toString())
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS)
        }.onFailure { log.warn("생성 메트릭 기록 실패(무시) type={} success={}", type, success, it) }
    }

    companion object {
        private const val MAX_REASON_LENGTH = 255
    }
}
