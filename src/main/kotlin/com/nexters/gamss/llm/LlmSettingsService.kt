package com.nexters.gamss.llm

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 생성 설정(모델·시스템 프롬프트) 조회·수정. DB에 값이 있으면 그것을, 없으면 코드 기본값
 * (GeminiProperties.model / PromptProvider.systemPrompt)을 쓴다. 생성기가 매 호출 [current]를 읽어
 * 재배포 없이 다음 생성부터 반영된다.
 *
 * 선택 가능한 모델은 [GeminiModelCatalog]가 Gemini API에서 동적으로 가져온다(신모델 자동 노출).
 */
@Service
class LlmSettingsService(
    private val repository: LlmSettingsRepository,
    private val geminiProperties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val modelCatalog: GeminiModelCatalog,
) {
    @Transactional(readOnly = true)
    fun current(): LlmSettingsView {
        val row = row()
        return LlmSettingsView(
            model = row?.model ?: geminiProperties.model,
            systemPrompt = row?.systemPrompt ?: promptProvider.systemPrompt,
        )
    }

    fun availableModels(): List<String> = modelCatalog.availableModels()

    @Transactional
    fun update(
        model: String,
        systemPrompt: String,
    ) {
        validateModel(model)
        val existing = row()
        if (existing != null) {
            existing.update(model, systemPrompt)
            return
        }
        repository.save(LlmSettings(model, systemPrompt))
    }

    // 기본 형식은 항상 검증하고, 카탈로그 조회가 성공한 경우엔 목록 소속까지 강제한다.
    // (API 장애로 목록이 비면 저장을 막지 않는다 — 형식만 통과하면 허용.)
    private fun validateModel(model: String) {
        if (model.isBlank() || !model.startsWith(GEMINI_PREFIX)) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 모델입니다: $model")
        }
        val available = modelCatalog.availableModels()
        if (available.isNotEmpty() && model !in available) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 모델입니다: $model")
        }
    }

    // 단일 행 유지. 없으면 null(코드 기본값 사용).
    private fun row(): LlmSettings? = repository.findAll().firstOrNull()

    companion object {
        private const val GEMINI_PREFIX = "gemini-"
    }
}
