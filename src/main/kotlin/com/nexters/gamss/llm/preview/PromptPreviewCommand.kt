package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 미리보기 실행 조건. 프롬프트 조각이 null이면 저장된 현재값을 쓴다. 캐릭터·티키타카는 항상
 * 호출자가 지정한다 - 조건이 고정돼야 프롬프트만 바꿔가며 여러 안을 비교할 수 있다.
 */
data class PromptPreviewCommand(
    val commonPrompt: String?,
    val commentPrompt: String?,
    val diaryContent: String,
    val currentConversationSummary: String?,
    val characters: List<EmotionType>,
    val tikitakaCount: Int?,
)
