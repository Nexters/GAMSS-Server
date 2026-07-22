package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 이번 피드에 등장할 캐릭터를 서버가 직접 무작위로 고른다. LLM에게 "무작위로 골라줘"라고 맡기면
 * 학습 데이터 편향으로 통계적 균등성이 보장되지 않는다(예: 특정 숫자·값에 쏠리는 현상이 다수 연구로
 * 확인됨). 프롬프트 캐싱은 "고정 캐릭터 설명 + 뒤에 선택 목록"으로 구조를 짜면 서버 선택이어도
 * 대부분 유지된다(Gemini 캐싱은 프리픽스 매칭 방식).
 */
@Component
class CharacterSelector(
    private val random: Random = Random.Default,
) {
    fun select(): List<EmotionType> {
        val count = random.nextInt(MIN_COUNT, MAX_COUNT + 1)
        return EmotionType.entries.shuffled(random).take(count)
    }

    /** 티키타카 개수도 LLM이 아니라 서버가 정해서 프롬프트에 박아 넣는다(같은 이유: LLM 무작위 신뢰 불가). */
    fun selectTikitakaCount(): Int = random.nextInt(TIKITAKA_MIN, TIKITAKA_MAX + 1)

    companion object {
        private const val MIN_COUNT = 3
        private const val MAX_COUNT = 6
        private const val TIKITAKA_MIN = 3
        private const val TIKITAKA_MAX = 4
    }
}
