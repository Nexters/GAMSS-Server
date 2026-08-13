package com.nexters.gamss.llm.generation

/**
 * 카드 생성 요청에 emotion이 없을 때, 유저가 보낸 메시지들만 보고 대표 감정 하나를 고른다.
 * 캐릭터 메시지의 감정(캐릭터가 낸 목소리)과 무관하게 유저의 감정을 분류하는 것이 계약이다.
 * 구현(Gemini 등)은 교체 가능하다([CardMessageGenerator]와 같은 패턴).
 */
interface EmotionExtractor {
    fun extract(userMessages: List<String>): EmotionExtractionOutput
}
