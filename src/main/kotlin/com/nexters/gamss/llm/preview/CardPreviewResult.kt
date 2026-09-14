package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.CardLineKind

/**
 * 카드 한 줄 미리보기 결과. [PromptPreviewResult]와 같은 이유로 실패도 오류 필드에 담는다.
 *
 * [line]은 실제 저장과 같은 규칙으로 다듬은 값이고, [rawLine]은 다듬기 전 값이다.
 * 둘을 함께 보여줘야 프롬프트가 길이 지시를 지키는지, 서버 truncate에 얼마나 기대고 있는지 알 수 있다.
 *
 * [emotion]은 실제 카드에 붙을 대표 감정이다. 판정이 [CardLineKind.NONSENSE]면 요청한 감정이 아니라
 * QUIRKY이고, 한 줄은 LLM이 쓴 문장이 아니라 서버가 고른 [eongttungTopic] 그대로다. [kind]는 판정 호출
 * 자체가 실패하면 null이다.
 */
data class CardPreviewResult(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val emotion: EmotionType,
    val kind: CardLineKind?,
    val eongttungTopic: String?,
    val line: String?,
    val rawLine: String?,
    val rawLength: Int?,
    val truncated: Boolean,
    val generationError: String?,
    val usage: PreviewUsage,
    val latencyMs: Long,
)
