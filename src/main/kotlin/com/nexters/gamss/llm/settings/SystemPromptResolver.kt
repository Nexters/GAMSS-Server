package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.stereotype.Component

/**
 * 생성기가 쓸 모델·시스템 프롬프트를 만든다. 모델은 앱 전체 단일값, 시스템 프롬프트는 [PromptType.COMMON] + 생성 타입 조립.
 * 설정 CRUD([LlmSettingsService])와는 별개인 '조립' 책임만 담당한다(SRP) — 백오피스는 원본을 편집하고, 생성기는 조립된 값을 읽는다.
 */
@Component
class SystemPromptResolver(
    private val llmSettingsService: LlmSettingsService,
) {
    /** 생성기가 쓸 모델·시스템 프롬프트. COMMON을 넘기면 조립 없이 공통 프롬프트만 돌려준다. */
    fun resolve(promptType: PromptType): LlmSettingsView {
        val base = llmSettingsService.currentCommonView()
        if (promptType == PromptType.COMMON) {
            return base
        }
        val typePrompt = llmSettingsService.currentPrompt(promptType)
        return LlmSettingsView(base.model, assemble(base.systemPrompt, typePrompt))
    }

    /**
     * 플레이그라운드 미리보기용 조립. null 인 조각은 저장된 현재값을 쓴다 - 미저장 편집본을
     * 실제 생성과 **같은 조립 규칙**으로 시험하기 위해 여기(조립의 단일 지점)에 둔다.
     */
    fun resolveForPreview(
        commonPrompt: String?,
        commentPrompt: String?,
    ): LlmSettingsView {
        val base = llmSettingsService.currentCommonView()
        val common = commonPrompt ?: base.systemPrompt
        val comment = commentPrompt ?: llmSettingsService.currentPrompt(PromptType.COMMENT)
        return LlmSettingsView(base.model, assemble(common, comment))
    }

    private fun assemble(
        commonPrompt: String,
        typePrompt: String,
    ): String = "$commonPrompt\n\n$typePrompt"
}
