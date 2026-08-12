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
 *
 * 후보 풀은 [EmotionType.SELECTABLE]이다 — 신규 생성 대상에서 빠진 캐릭터는 과거 레코드로만 남고 새로 뽑히지 않는다.
 * [excludedCharacters]로 대화방이 제외한 캐릭터를 걸러낸 후보 풀에서 뽑는다. 후보가
 * [MIN_CHARACTERS_FOR_TIKITAKA]명 미만(즉 1명)이면 애초에 티키타카가 성립할 수 없으므로 total 자체를
 * [TOTAL_MIN]으로 강제한다 — 그러지 않으면 characterCount 하한(2)이 후보 풀(1명)보다 커져 범위가 깨진다.
 */
@Component
class CharacterSelector(
    private val random: Random = Random.Default,
) {
    fun select(excludedCharacters: Set<EmotionType> = emptySet()): CharacterSelection {
        val candidates = EmotionType.SELECTABLE.filterNot { it in excludedCharacters }
        require(candidates.isNotEmpty()) { "excludedCharacters가 전체 캐릭터를 제외했습니다: $excludedCharacters" }
        val poolSize = candidates.size
        val total =
            if (poolSize < MIN_CHARACTERS_FOR_TIKITAKA) {
                TOTAL_MIN
            } else {
                random.nextInt(TOTAL_MIN, TOTAL_MAX + 1)
            }
        val maxCharacterCount = minOf(total, poolSize)
        val characterCount = random.nextInt(minOf(MIN_CHARACTERS_FOR_TIKITAKA, maxCharacterCount), maxCharacterCount + 1)
        val tikitakaCount = total - characterCount
        val characters = candidates.shuffled(random).take(characterCount)
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
) {
    companion object {
        /**
         * 고정 선택용 팩토리(플레이그라운드 등). 중복을 제거하고 선택 불변식 - 캐릭터 1명 이상,
         * 신규 생성 대상인 캐릭터만, 티키타카는 캐릭터 2명 이상일 때만 - 을 [CharacterSelector]의
         * 무작위 선택과 똑같이 보장한다.
         */
        fun of(
            characters: List<EmotionType>,
            tikitakaCount: Int,
        ): CharacterSelection {
            val distinct = characters.distinct()
            require(distinct.isNotEmpty()) { "캐릭터를 1명 이상 지정해야 합니다." }
            // 신규 생성에서 빠진 캐릭터는 보이스 카드도 프롬프트에서 빠져 실제 생성과 다른 결과를 보게 된다.
            // 실서비스와 같은 경로를 시험하는 게 플레이그라운드의 목적이므로 여기서 막는다.
            val unselectable = distinct.filterNot { it.selectable }
            require(unselectable.isEmpty()) { "생성에 쓰이지 않는 캐릭터는 지정할 수 없습니다: ${unselectable.joinToString { it.label }}" }
            require(tikitakaCount >= 0) { "tikitakaCount는 0 이상이어야 합니다." }
            require(tikitakaCount == 0 || distinct.size >= 2) { "티키타카는 캐릭터가 2명 이상일 때만 지정할 수 있습니다." }
            return CharacterSelection(distinct, tikitakaCount)
        }
    }
}
