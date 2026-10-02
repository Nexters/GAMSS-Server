package com.nexters.gamss.llm.prompt

import com.nexters.gamss.emotion.domain.EmotionType

/** 대화 기록의 말 한 마디. [speaker]가 null이면 유저가 한 말이다. */
data class TranscriptEntry(
    val speaker: EmotionType?,
    val content: String,
)
