package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 미리보기 실행 조건. 프롬프트 조각이 null이면 저장된 현재값을 쓰고, [characters]가 null이면
 * 실제 생성처럼 서버가 무작위로 고른다(고정하면 같은 조건으로 여러 안을 비교할 수 있다).
 */
data class PromptPreviewCommand(
    val commonPrompt: String?,
    val commentPrompt: String?,
    val diaryContent: String,
    val currentConversationSummary: String?,
    val characters: List<EmotionType>?,
    val tikitakaCount: Int?,
)
