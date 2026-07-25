package com.nexters.gamss.llm

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 생성 설정(모델·시스템 프롬프트) 조회·수정. [PromptType]별로 독립된 행을 가진다. DB에 값이
 * 있으면 그것을, 없으면 코드 기본값(GeminiProperties.model / PromptProvider의 타입별 프롬프트)을 쓴다.
 *
 * 실제 시스템 프롬프트는 **[PromptType.COMMON] + 생성 타입**을 조립한 값이다([forGeneration]).
 * 백오피스는 공통·타입별을 각각 편집하고([current]/[update]), 생성기는 조립된 값을 읽어 재배포 없이 반영한다.
 * 선택 가능한 모델은 [GeminiModelCatalog]가 Gemini API에서 동적으로 가져온다(신모델 자동 노출).
 */
@Service
class LlmSettingsService(
    private val repository: LlmSettingsRepository,
    private val geminiProperties: GeminiProperties,
    private val promptProvider: PromptProvider,
    private val modelCatalog: GeminiModelCatalog,
) {
    /** 특정 타입의 원본 설정(백오피스 편집·조회용). 조립하지 않은 그 타입의 값 그대로다. */
    @Transactional(readOnly = true)
    fun current(promptType: PromptType): LlmSettingsView {
        val row = row(promptType)
        return LlmSettingsView(
            model = row?.model ?: geminiProperties.model,
            systemPrompt = row?.systemPrompt ?: defaultPrompt(promptType),
        )
    }

    /**
     * 생성기가 쓸 모델·시스템 프롬프트. 시스템 프롬프트는 [PromptType.COMMON] + 해당 타입으로 조립한다.
     * (COMMON 자체를 넘기면 조립 없이 공통 프롬프트만 돌려준다.)
     */
    @Transactional(readOnly = true)
    fun forGeneration(promptType: PromptType): LlmSettingsView {
        val typeSetting = current(promptType)
        if (promptType == PromptType.COMMON) {
            return typeSetting
        }
        val common = current(PromptType.COMMON).systemPrompt
        return LlmSettingsView(
            model = typeSetting.model,
            systemPrompt = "$common\n\n${typeSetting.systemPrompt}",
        )
    }

    fun availableModels(): List<String> = modelCatalog.availableModels()

    /** 코드 기본값(DB로 덮이기 전 원래 값). "기본값으로 복원"에 쓴다. */
    fun defaults(promptType: PromptType): LlmSettingsView = LlmSettingsView(geminiProperties.model, defaultPrompt(promptType))

    @Transactional
    fun update(
        promptType: PromptType,
        model: String?,
        systemPrompt: String,
    ) {
        val resolvedModel = resolveModel(promptType, model)
        val existing = row(promptType)
        if (existing != null) {
            existing.update(resolvedModel, systemPrompt)
            return
        }
        repository.save(LlmSettings(promptType, resolvedModel, systemPrompt))
    }

    private fun defaultPrompt(promptType: PromptType): String =
        when (promptType) {
            PromptType.COMMON -> promptProvider.commonPrompt
            PromptType.COMMENT -> promptProvider.commentPrompt
            PromptType.REPLY -> promptProvider.replyPrompt
            PromptType.CARD -> promptProvider.cardPrompt
        }

    // COMMON은 모델을 쓰지 않으므로 코드 기본 모델을 자리표시자로 저장한다(생성엔 안 쓰임).
    // 나머지 타입은 모델이 필수이고 형식·목록 검증을 거친다.
    private fun resolveModel(
        promptType: PromptType,
        model: String?,
    ): String {
        if (promptType == PromptType.COMMON) {
            return geminiProperties.model
        }
        if (model.isNullOrBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "model은 필수입니다.")
        }
        validateModel(model)
        return model
    }

    // 기본 형식은 항상 검증하고, 카탈로그 조회가 성공한 경우엔 목록 소속까지 강제한다.
    // (API 장애로 목록이 비면 저장을 막지 않는다 — 형식만 통과하면 허용.)
    private fun validateModel(model: String) {
        if (!model.startsWith(GEMINI_PREFIX)) {
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
