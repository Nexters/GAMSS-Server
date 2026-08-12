package com.nexters.gamss.llm.prompt
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.error.CommentGenerationFailedException

/**
 * 프롬프트가 실제로 쓰는 로마자 캐릭터 id(gippeum, seulpeum...) <-> 내부 [EmotionType] 매핑.
 * EmotionType.label(기쁨/슬픔/분노/불안/까칠/엉뚱)과 1:1 대응된다. 프롬프트 어휘가 도메인 enum(EmotionType)
 * 안으로 새어 들어오지 않도록 이 파일과 [PromptProvider], [CommentFeedJsonParser] 안에서만 번역한다.
 *
 * 신규 생성 대상에서 빠진 캐릭터(dajeong)도 매핑을 남긴다 — 새로 뽑히지는 않지만 과거에
 * 다정이가 단 댓글의 답글·카드 생성이 여전히 이 id를 프롬프트에 실어 보내기 때문이다.
 */
enum class PromptCharacterId(
    val emotionType: EmotionType,
) {
    GIPPEUM(EmotionType.JOY),
    SEULPEUM(EmotionType.SADNESS),
    BUNNO(EmotionType.ANGER),
    BULAN(EmotionType.ANXIETY),
    KKACHIL(EmotionType.GRUMPY),
    EONGTTUNG(EmotionType.QUIRKY),
    DAJEONG(EmotionType.WARM),
    ;

    val promptId: String get() = name.lowercase()

    companion object {
        fun of(emotionType: EmotionType): PromptCharacterId = entries.first { it.emotionType == emotionType }

        fun fromPromptId(promptId: String): PromptCharacterId =
            entries.firstOrNull { it.promptId == promptId }
                ?: throw CommentGenerationFailedException("알 수 없는 character_id: $promptId")
    }
}
