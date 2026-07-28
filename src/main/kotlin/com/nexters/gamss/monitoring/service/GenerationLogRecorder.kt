package com.nexters.gamss.monitoring.service

import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * LLM 생성 1요청을 관측 로그로 남긴다(SRP: 기록만 담당). 모델은 앱 전체 단일값이라 여기서 스탬프한다.
 * 기록 실패가 생성 흐름을 절대 깨지 않도록 예외를 삼킨다 — 모니터링 로그는 부가 기능이기 때문이다.
 */
@Component
class GenerationLogRecorder(
    private val generationLogRepository: GenerationLogRepository,
    private val llmSettingsService: LlmSettingsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(
        type: GenerationType,
        success: Boolean,
        attemptCount: Int,
        latencyMs: Long,
        usedTokens: Int? = null,
        cachedTokens: Int? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        failureReason: String? = null,
    ) {
        runCatching {
            generationLogRepository.save(
                GenerationLog(
                    generationType = type,
                    model = llmSettingsService.currentModel(),
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

    companion object {
        private const val MAX_REASON_LENGTH = 255
    }
}
