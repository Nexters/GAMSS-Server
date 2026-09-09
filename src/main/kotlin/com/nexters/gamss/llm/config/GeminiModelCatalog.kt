package com.nexters.gamss.llm.config

import com.google.genai.types.ListModelsConfig
import com.nexters.gamss.llm.provider.GeminiConnectionService
import com.nexters.gamss.llm.provider.LlmProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 백오피스 드롭다운에 쓸 "선택 가능한 Gemini 모델" 목록을 Gemini API에서 동적으로 가져온다.
 * 새 모델이 출시되면 코드 수정·재배포 없이 자동으로 목록에 뜬다.
 *
 * 필터: 이름이 gemini- 로 시작하는 생성 모델만(임베딩 제외). supportedActions 는 개발자 API 응답에서
 * 비어 올 수 있어 의존하지 않고 이름으로 거른다.
 * 캐시는 호출 경로별로 나눈다 — 경로마다 쓸 수 있는 모델이 다를 수 있어, 전환 직후 이전 경로의
 * 목록을 그대로 보여주면 안 된다. [CACHE_TTL_MS] 동안 유지하고 조회 실패 시 직전 캐시(없으면 빈 목록)로 폴백한다.
 */
@Component
class GeminiModelCatalog(
    private val connections: GeminiConnectionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<LlmProvider, CachedModels>()

    fun availableModels(): List<String> {
        val provider = connections.activeProvider()
        val now = System.currentTimeMillis()
        val snapshot = cache[provider]
        if (snapshot != null && now - snapshot.atEpochMs < CACHE_TTL_MS) {
            return snapshot.models
        }
        return runCatching { fetch() }
            .onSuccess { cache[provider] = CachedModels(it, now) }
            .getOrElse { e ->
                log.warn("Gemini 모델 목록 조회 실패 — 이전 캐시로 폴백한다", e)
                snapshot?.models ?: emptyList()
            }
    }

    // 이름 형식이 경로마다 다르다(AI Studio는 models/..., Vertex는 publishers/google/models/...).
    private fun fetch(): List<String> =
        connections
            .activeClient()
            .models
            .list(ListModelsConfig.builder().queryBase(true).build())
            .asSequence()
            .mapNotNull { it.name().orElse(null) }
            .map { it.substringAfterLast('/') }
            .filter { it.startsWith("gemini-") && "embedding" !in it }
            .distinct()
            .sorted()
            .toList()

    private data class CachedModels(
        val models: List<String>,
        val atEpochMs: Long,
    )

    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1시간
    }
}
