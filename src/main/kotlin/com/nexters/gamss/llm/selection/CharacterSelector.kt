package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 이번 피드에 등장할 캐릭터 + 티키타카 개수를 서버가 직접 무작위로 고른다. LLM에게 "무작위로 골라줘"라고
 * 맡기면 학습 데이터 편향으로 통계적 균등성이 보장되지 않는다(예: 특정 숫자·값에 쏠리는 현상이 다수
 * 연구로 확인됨). 프롬프트 캐싱은 "고정 캐릭터 설명 + 뒤에 선택 목록"으로 구조를 짜면 서버 선택이어도
 * 대부분 유지된다(Gemini 캐싱은 프리픽스 매칭 방식).
 *
 * 캐릭터 수와 티키타카 수는 각각 뽑지 않고, 둘의 합([TOTAL_MIN]~[TOTAL_MAX])을 먼저 정한 뒤 그 안에서
 * 나눈다. 티키타카는 캐릭터끼리 주고받는 것이라 캐릭터가 [MIN_CHARACTERS_FOR_TIKITAKA]명 미만이면
 * 성립할 수 없다 — 이 제약을 나중에 걸러내면 남은 예산이 갈 곳을 잃고 증발하므로, characterCount를
 * 뽑는 범위 자체에 미리 반영해 total이 항상 그대로 소진되게 한다.
 */
@Component
class CharacterSelector(
    private val random: Random = Random.Default,
) {
    fun select(): CharacterSelection {
        val total = random.nextInt(TOTAL_MIN, TOTAL_MAX + 1)
        val characterCount =
            if (total < MIN_CHARACTERS_FOR_TIKITAKA) {
                total
            } else {
                random.nextInt(MIN_CHARACTERS_FOR_TIKITAKA, total + 1)
            }
        val tikitakaCount = total - characterCount
        val characters = EmotionType.entries.shuffled(random).take(characterCount)
        return CharacterSelection(characters, tikitakaCount)
    }

    companion object {
        private const val TOTAL_MIN = 1
        private const val TOTAL_MAX = 3
        private const val MIN_CHARACTERS_FOR_TIKITAKA = 2
    }
}

data class CharacterSelection(
    val characters: List<EmotionType>,
    val tikitakaCount: Int,
)
