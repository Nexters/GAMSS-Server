package com.nexters.gamss.llm.parsing

/**
 * LLM 1회 호출로 생성되는 피드 전체(1라운드 댓글 + 2라운드 티키타카).
 * 정본 스키마: 걱정인형의방_실험_기반_스키마_캐릭터.md, harness/schema.py
 */
data class CommentFeed(
    val comments: List<CommentDraft>,
    val tikitaka: List<TikitakaDraft>,
)
