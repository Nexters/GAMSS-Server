package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.config.GeminiModelCatalog
import com.nexters.gamss.llm.config.GeminiProperties
import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 생성 설정 조회·수정. 두 종류를 관리한다:
 * - **모델**: 앱 전체 단일 설정(댓글·답글·카드가 같은 모델 사용). 물리적으로는 [PromptType.COMMON] 행에 저장한다.
 * - **프롬프트**: [PromptType]별 원본(백오피스 편집 단위). 생성기가 쓰는 조립본(COMMON + 타입)은 [SystemPromptResolver]가 만든다.
 *
 * 프롬프트의 단일 원본은 DB다 — V23 시딩이 모든 타입의 행을 보장하므로 코드 폴백은 없고,
 * 행이 없으면 마이그레이션이 안 돈 것이라 즉시 실패시킨다.
 * 선택 가능한 모델은 [GeminiModelCatalog]가 Gemini API에서 동적으로 가져온다(신모델 자동 노출).
 */
@Service
class LlmSettingsService(
    private val repository: LlmSettingsRepository,
    private val geminiProperties: GeminiProperties,
    private val modelCatalog: GeminiModelCatalog,
) {
    // ── 모델(앱 전체 단일). COMMON 행의 model 컬럼에 저장한다. ──

    @Transactional(readOnly = true)
    fun currentModel(): String = requireRow(PromptType.COMMON).model

    fun defaultModel(): String = geminiProperties.model

    fun availableModels(): List<String> = modelCatalog.availableModels()

    @Transactional
    fun updateModel(model: String) {
        validateModel(model)
        val row = requireRow(PromptType.COMMON)
        row.update(model, row.systemPrompt)
    }

    // ── 프롬프트(타입별 원본). ──

    @Transactional(readOnly = true)
    fun currentPrompt(promptType: PromptType): String = requireRow(promptType).systemPrompt

    /**
     * 행을 잠그고 현재 프롬프트를 읽는다 — 프롬프트 저장·복원의 리비전 채번을 타입 단위로
     * 직렬화하는 잠금 지점([PromptRevisionService]). 행이 없으면 null(잠글 대상 없음).
     */
    @Transactional
    fun currentPromptForUpdate(promptType: PromptType): String? = repository.findByPromptTypeForUpdate(promptType)?.systemPrompt

    /**
     * 행을 잠그고 현재 프롬프트를 읽는다 — 프롬프트 저장·복원의 리비전 채번을 타입 단위로
     * 직렬화하는 잠금 지점([PromptRevisionService]). 행이 없으면 null(잠글 대상 없음).
     */
    @Transactional
    fun currentPromptForUpdate(promptType: PromptType): String? = repository.findByPromptTypeForUpdate(promptType)?.systemPrompt

    /**
     * COMMON 행을 한 번만 읽어 앱 전체 모델 + 공통 프롬프트를 함께 돌려준다.
     * 모델·공통 프롬프트가 같은 COMMON 행에서 나오므로, [SystemPromptResolver]가 조립할 때
     * COMMON 행을 중복 조회하지 않게 한다.
     */
    @Transactional(readOnly = true)
    fun currentCommonView(): LlmSettingsView {
        val common = requireRow(PromptType.COMMON)
        return LlmSettingsView(common.model, common.systemPrompt)
    }

    @Transactional
    fun updatePrompt(
        promptType: PromptType,
        systemPrompt: String,
    ) {
        val row = requireRow(promptType)
        row.update(row.model, systemPrompt)
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

    // promptType당 행 1개(unique 제약 + V23 시딩 보장). 없으면 마이그레이션 누락이므로 즉시 실패.
    private fun requireRow(promptType: PromptType): LlmSettings =
        checkNotNull(repository.findByPromptType(promptType)) {
            "llm_settings에 $promptType 행이 없습니다. V23 시딩 마이그레이션이 적용됐는지 확인하세요."
        }

    companion object {
        private const val GEMINI_PREFIX = "gemini-"
    }
}
