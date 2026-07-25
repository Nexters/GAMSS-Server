package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiModelCatalog
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.prompt.PromptProvider
import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 생성 설정 조회·수정. 두 종류를 관리한다:
 * - **모델**: 앱 전체 단일 설정(댓글·답글·카드가 같은 모델 사용). 물리적으로는 [PromptType.COMMON] 행에 저장한다.
 * - **프롬프트**: [PromptType]별 원본(백오피스 편집 단위). 생성기가 쓰는 조립본(COMMON + 타입)은 [SystemPromptResolver]가 만든다.
 *
 * DB에 값이 없으면 코드 기본값(GeminiProperties.model / PromptProvider의 타입별 프롬프트)을 쓴다.
 * 선택 가능한 모델은 [GeminiModelCatalog]가 Gemini API에서 동적으로 가져온다(신모델 자동 노출).
 */
@Service
class LlmSettingsService(
    private val repository: LlmSettingsRepository,
    private val geminiProperties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val modelCatalog: GeminiModelCatalog,
) {
    // ── 모델(앱 전체 단일). COMMON 행의 model 컬럼에 저장한다. ──

    @Transactional(readOnly = true)
    fun currentModel(): String = row(PromptType.COMMON)?.model ?: geminiProperties.model

    fun defaultModel(): String = geminiProperties.model

    fun availableModels(): List<String> = modelCatalog.availableModels()

    @Transactional
    fun updateModel(model: String) {
        validateModel(model)
        val row = row(PromptType.COMMON)
        if (row != null) {
            row.update(model, row.systemPrompt)
            return
        }
        repository.save(LlmSettings(PromptType.COMMON, model, promptProvider.defaultPrompt(PromptType.COMMON)))
    }

    // ── 프롬프트(타입별 원본). ──

    @Transactional(readOnly = true)
    fun currentPrompt(promptType: PromptType): String = row(promptType)?.systemPrompt ?: promptProvider.defaultPrompt(promptType)

    fun defaultPrompt(promptType: PromptType): String = promptProvider.defaultPrompt(promptType)

    @Transactional
    fun updatePrompt(
        promptType: PromptType,
        systemPrompt: String,
    ) {
        val row = row(promptType)
        if (row != null) {
            row.update(row.model, systemPrompt)
            return
        }
        // 새 행의 model 컬럼: COMMON이면 곧 전체 모델, 그 외 타입은 안 쓰이는 자리표시자다.
        repository.save(LlmSettings(promptType, currentModel(), systemPrompt))
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

    // promptType당 행 최대 1개(unique 제약). 없으면 null(코드 기본값 사용).
    private fun row(promptType: PromptType): LlmSettings? = repository.findByPromptType(promptType)

    companion object {
        private const val GEMINI_PREFIX = "gemini-"
    }
}
