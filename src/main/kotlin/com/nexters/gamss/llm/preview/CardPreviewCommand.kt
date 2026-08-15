package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 카드 한 줄 미리보기 실행 조건 - [emotion]을 대표 감정으로 두고 [summary]를 다듬은 한 줄을 시험한다.
 * [cardPrompt]가 null이면 저장된 현재값을 쓴다. 공통 프롬프트를 받지 않는 것은 카드 프롬프트가
 * 조립되지 않기 때문이다([com.nexters.gamss.llm.settings.SystemPromptResolver]).
 */
data class CardPreviewCommand(
    val cardPrompt: String?,
    val emotion: EmotionType,
    val summary: String,
)
