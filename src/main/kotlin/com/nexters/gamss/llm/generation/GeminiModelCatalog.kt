package com.nexters.gamss.llm.generation

import com.google.genai.Client
import com.google.genai.types.ListModelsConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 백오피스 드롭다운에 쓸 "선택 가능한 Gemini 모델" 목록을 Gemini API에서 동적으로 가져온다.
 * 새 모델이 출시되면 코드 수정·재배포 없이 자동으로 목록에 뜬다.
 *
 * 필터: 이름이 gemini- 로 시작하는 생성 모델만(임베딩 제외). supportedActions 는 개발자 API 응답에서
 * 비어 올 수 있어 의존하지 않고 이름으로 거른다.
 * 결과는 [CACHE_TTL_MS] 동안 캐시하고, 조회 실패 시 직전 캐시(없으면 빈 목록)로 폴백한다.
 */
@Component
class GeminiModelCatalog(
    private val properties: GeminiProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: Client by lazy { Client.builder().apiKey(properties.apiKey).build() }

    @Volatile
    private var cached: List<String> = emptyList()

    @Volatile
    private var cachedAtEpochMs: Long = 0L

    fun availableModels(): List<String> {
        val now = System.currentTimeMillis()
        val snapshot = cached
        if (snapshot.isNotEmpty() && now - cachedAtEpochMs < CACHE_TTL_MS) {
            return snapshot
        }
        return runCatching { fetch() }
            .onSuccess {
                cached = it
                cachedAtEpochMs = now
            }.getOrElse { e ->
                log.warn("Gemini 모델 목록 조회 실패 — 이전 캐시로 폴백한다", e)
                snapshot
            }
    }

    private fun fetch(): List<String> =
        client.models
            .list(ListModelsConfig.builder().queryBase(true).build())
            .asSequence()
            .mapNotNull { it.name().orElse(null) }
            .map { it.removePrefix("models/") }
            .filter { it.startsWith("gemini-") && "embedding" !in it }
            .distinct()
            .sorted()
            .toList()

    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1시간
    }
}
