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
        return LlmSettingsView(base.model, "${base.systemPrompt}\n\n$typePrompt")
    }
}
