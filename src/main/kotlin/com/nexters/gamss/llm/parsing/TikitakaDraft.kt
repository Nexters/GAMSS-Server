package com.nexters.gamss.llm.parsing

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 2라운드: 캐릭터끼리의 티키타카. [replyTo]는 DB PK가 아니라 1라운드 캐릭터를 가리키는 논리 참조다
 * (1라운드에서 캐릭터당 댓글이 정확히 하나이므로 characterId로 유일하게 특정된다).
 */
data class TikitakaDraft(
    val characterId: EmotionType,
    val replyTo: EmotionType,
    val text: String,
)
