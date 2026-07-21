package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 프롬프트가 실제로 쓰는 로마자 캐릭터 id(gippeum, dajeong...) <-> 내부 [EmotionType] 매핑.
 * EmotionType.label(기쁨/다정/분노/불안/까칠/엉뚱)과 1:1 대응된다. 프롬프트 어휘가 도메인 enum(EmotionType)
 * 안으로 새어 들어오지 않도록 이 파일과 [PromptProvider], [CommentFeedJsonParser] 안에서만 번역한다.
 */
enum class PromptCharacterId(
    val emotionType: EmotionType,
) {
    GIPPEUM(EmotionType.JOY),
    DAJEONG(EmotionType.WARM),
    BUNNO(EmotionType.ANGER),
    BULAN(EmotionType.ANXIETY),
    KKACHIL(EmotionType.GRUMPY),
    EONGTTUNG(EmotionType.QUIRKY),
    ;

    val promptId: String get() = name.lowercase()

    companion object {
        fun of(emotionType: EmotionType): PromptCharacterId = entries.first { it.emotionType == emotionType }

        fun fromPromptId(promptId: String): PromptCharacterId =
            entries.firstOrNull { it.promptId == promptId }
                ?: throw CommentGenerationFailedException("알 수 없는 character_id: $promptId")
    }
}
