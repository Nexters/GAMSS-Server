package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 일기 내용과 이번 생성 조건(등장 캐릭터·티키타카 개수·엉뚱이 소재)으로 댓글 피드를 생성하거나,
 * 유저가 캐릭터 댓글에 단 답글에 그 캐릭터가 재응답한다. 구현(Gemini 등)은 교체 가능하다
 * ([com.nexters.gamss.auth.social.SocialTokenVerifier]와 같은 패턴).
 *
 * 구조적으로 잘못된 응답(JSON 파싱 실패 등)은 이 단계에서 걸러지지만, 의미 검증(캐릭터 중복,
 * 존재하지 않는 참조 등)은 하지 않는다 — [CommentFeedValidator]가 별도로 담당한다.
 */
interface CommentGenerator {
    fun generateComment(
        pastSummary: String,
        diaryContent: String,
        characters: List<EmotionType>,
        tikitakaCount: Int,
        eongttungTopic: String?,
    ): CommentGenerationOutput

    fun generateReply(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
    ): ReplyGenerationOutput
}

/** LLM 호출 1회의 결과. [usedTokens]는 파싱된 [feed]와 별개로 호출 자체의 메타데이터(과금 단위)다. */
data class CommentGenerationOutput(
    val feed: CommentFeed,
    val usedTokens: Int,
)

/** 답글 생성 LLM 호출 1회의 결과. */
data class ReplyGenerationOutput(
    val text: String,
    val usedTokens: Int,
)
