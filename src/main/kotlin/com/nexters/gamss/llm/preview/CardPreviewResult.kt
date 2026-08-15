package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 카드 한 줄 미리보기 결과. [PromptPreviewResult]와 같은 이유로 실패도 오류 필드에 담는다.
 *
 * [line]은 실제 저장과 같은 규칙으로 다듬은 값이고, [rawLine]은 LLM이 그대로 돌려준 값이다.
 * 둘을 함께 보여줘야 프롬프트가 길이 지시를 지키는지, 서버 truncate에 얼마나 기대고 있는지 알 수 있다.
 */
data class CardPreviewResult(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val emotion: EmotionType,
    val line: String?,
    val rawLine: String?,
    val rawLength: Int?,
    val truncated: Boolean,
    val generationError: String?,
    val usage: PreviewUsage,
    val latencyMs: Long,
)
