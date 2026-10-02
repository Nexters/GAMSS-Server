package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 유저 메시지가 어떤 캐릭터를 불렀는지 판정한다. 판정은 LLM 호출 **전에** 서버가 한다. 그래야 캐릭터
 * 구성을 미리 정할 수 있고, 재시도해도 같은 판정이 나온다.
 *
 * 구현은 교체할 수 있다. 규칙 기반 구현이 놓치는 호명이 많다고 확인되면 LLM 분류 구현으로 바꾸면 된다.
 */
interface AddresseeResolver {
    /** 부른 캐릭터를 메시지에 처음 등장한 순서대로 중복 없이 돌려준다. 막아둔 캐릭터인지는 따지지 않는다. */
    fun resolve(message: String): List<EmotionType>
}
