package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component

/**
 * 유저 메시지에 누가 답할지 정한다. 호명 판정은 [AddresseeResolver], 무작위 선택은 [CharacterSelector]가
 * 맡고, 여기는 둘 중 무엇을 쓸지만 정한다.
 *
 * - 이 방의 캐릭터를 불렀으면 부른 캐릭터만 각자 답하고 티키타카는 없다. 유저가 특정 캐릭터에게 한 말에
 *   다른 캐릭터가 끼어들면 대화가 어색해진다.
 * - 막아둔 캐릭터만 불렀거나 아무도 부르지 않았으면 지금처럼 무작위로 고른다. 막아둔 캐릭터는 여기서도
 *   후보에서 빠지므로 어떤 경우에도 등장하지 않는다.
 */
@Component
class ResponsePlanner(
    private val characterSelector: CharacterSelector,
    private val addresseeResolver: AddresseeResolver,
) {
    fun plan(
        message: String,
        excludedCharacters: Set<EmotionType>,
    ): ResponsePlan {
        val addressees = Addressees.of(addresseeResolver.resolve(message), excludedCharacters)
        if (addressees.present.isEmpty()) {
            return ResponsePlan(characterSelector.select(excludedCharacters), addressees)
        }
        return ResponsePlan(CharacterSelection.of(addressees.present, 0), addressees)
    }
}
