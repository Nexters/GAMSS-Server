package com.nexters.gamss.llm.parsing

import com.nexters.gamss.emotion.domain.EmotionType

/** 1라운드: 캐릭터가 일기에 다는 댓글. */
data class CommentDraft(
    val characterId: EmotionType,
    val text: String,
)
