package com.nexters.gamss.llm.parsing

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * LLM 1회 호출로 생성되는 피드 전체(1라운드 댓글 + 2라운드 티키타카).
 * 정본 스키마: 걱정인형의방_실험_기반_스키마_캐릭터.md, harness/schema.py
 */
data class CommentFeed(
    val comments: List<CommentDraft>,
    val tikitaka: List<TikitakaDraft>,
)

/** 1라운드: 캐릭터가 일기에 다는 댓글. */
data class CommentDraft(
    val characterId: EmotionType,
    val text: String,
)

/**
 * 2라운드: 캐릭터끼리의 티키타카. [replyTo]는 DB PK가 아니라 1라운드 캐릭터를 가리키는 논리 참조다
 * (1라운드에서 캐릭터당 댓글이 정확히 하나이므로 characterId로 유일하게 특정된다).
 */
data class TikitakaDraft(
    val characterId: EmotionType,
    val replyTo: EmotionType,
    val text: String,
)
