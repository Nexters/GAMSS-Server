package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 대화 요약과 대표 감정으로, 그 감정 캐릭터가 대화를 대표해 남길 카드 한 줄 대사를 생성한다.
 * 구현(Gemini 등)은 교체 가능하다([CommentGenerator]와 같은 패턴). 카드 대사는 대화용 댓글과
 * 목적·프롬프트가 다르므로 [CommentGenerator]와 별개의 생성 경로·프롬프트([CardPromptProvider])를 쓴다.
 */
interface CardMessageGenerator {
    fun generate(
        emotion: EmotionType,
        summary: String,
    ): CardMessageOutput
}

/** 카드 대사 생성 1회의 결과. [usedTokens]는 호출 자체의 과금 단위다. */
data class CardMessageOutput(
    val message: String,
    val usedTokens: Int,
)

/** 카드 대사 LLM 호출 실패(네트워크·타임아웃) 또는 응답 파싱 실패를 아우르는 예외. 재시도 대상. */
class CardGenerationFailedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
