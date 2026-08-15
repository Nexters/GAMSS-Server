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
        // 소재 목록은 시스템 프롬프트 조각이 아니다 — 조립하면 "공통 + 소재 나열"이라는
        // 성립 불가능한 프롬프트가 조용히 만들어지므로 호출 자체를 계약 위반으로 막는다.
        require(promptType != PromptType.EONGTTUNG_TOPIC) { "EONGTTUNG_TOPIC은 시스템 프롬프트로 조립할 수 없습니다." }
        // 감정 분류는 캐릭터 톤·말맛 지침(COMMON)과 무관한 작업이라 조립하면 분류 정확도만 흐린다 —
        // 단독 프롬프트로 쓰도록 조립을 막는다(GeminiEmotionExtractor가 원본을 직접 읽는다).
        require(promptType != PromptType.CARD_EMOTION) { "CARD_EMOTION은 시스템 프롬프트로 조립할 수 없습니다." }
        // 카드 한 줄은 캐릭터 대사가 아니라 유저 시점의 하루 기록이라 캐릭터 보이스 카드가 필요 없다.
        // 조립하면 COMMON의 "보이스 카드 말투를 철저히 지켜라"와 CARD의 "캐릭터 말투를 쓰지 마라"가
        // 한 프롬프트 안에서 정면으로 충돌한다(GeminiCardMessageGenerator가 원본을 직접 읽는다).
        require(promptType != PromptType.CARD) { "CARD는 시스템 프롬프트로 조립할 수 없습니다." }
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
        promptType: PromptType,
        commonPrompt: String?,
        typePrompt: String?,
    ): LlmSettingsView {
        require(promptType != PromptType.COMMON) { "조립할 타입 프롬프트를 지정해야 합니다." }
        // resolve()와 같은 이유 - 소재 목록·감정 분류·카드 한 줄은 시스템 프롬프트 조각이 아니다.
        require(promptType != PromptType.EONGTTUNG_TOPIC) { "EONGTTUNG_TOPIC은 시스템 프롬프트로 조립할 수 없습니다." }
        require(promptType != PromptType.CARD_EMOTION) { "CARD_EMOTION은 시스템 프롬프트로 조립할 수 없습니다." }
        require(promptType != PromptType.CARD) { "CARD는 시스템 프롬프트로 조립할 수 없습니다." }
        val base = llmSettingsService.currentCommonView()
        val common = commonPrompt ?: base.systemPrompt
        val type = typePrompt ?: llmSettingsService.currentPrompt(promptType)
        return LlmSettingsView(base.model, assemble(common, type))
    }

    private fun assemble(
        commonPrompt: String,
        typePrompt: String,
    ): String = "$commonPrompt\n\n$typePrompt"
}
