package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 유저가 부른 캐릭터를 이 방에 있는 캐릭터([present])와 막아둔 캐릭터([absent])로 나눈 결과.
 * 둘 다 메시지에 처음 나온 순서를 유지한다.
 */
data class Addressees(
    val present: List<EmotionType>,
    val absent: List<EmotionType>,
) {
    companion object {
        val NONE = Addressees(emptyList(), emptyList())

        fun of(
            called: List<EmotionType>,
            excluded: Set<EmotionType>,
        ): Addressees = Addressees(called.filterNot { it in excluded }, called.filter { it in excluded })
    }
}
