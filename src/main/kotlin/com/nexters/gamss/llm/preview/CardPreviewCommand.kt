package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 카드 한 줄 미리보기 실행 조건 - 실제 카드 생성과 같은 입력을 만든다. [userMessages]가 사실의 기준이고
 * [summary]는 참고다. [summary]를 비우면 요약이 없는 새벽 배치와 같은 조건이 된다.
 *
 * [cardPrompt]가 null이면 저장된 현재값을 쓴다. 공통 프롬프트를 받지 않는 것은 카드 프롬프트가
 * 조립되지 않기 때문이다([com.nexters.gamss.llm.settings.SystemPromptResolver]).
 */
data class CardPreviewCommand(
    val cardPrompt: String?,
    val emotion: EmotionType,
    val userMessages: List<String>,
    val summary: String?,
)
