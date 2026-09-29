package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType

/** 이번 응답에 누가 몇 번 말할지([selection])와, 그렇게 정한 근거가 된 호명([addressees]). */
data class ResponsePlan(
    val selection: CharacterSelection,
    val addressees: Addressees,
) {
    /**
     * 엉뚱이에게 소재를 줄지. 엉뚱이는 평소 기록 내용과 상관없이 주어진 소재로 딴소리를 한다. 하지만 유저가
     * 엉뚱이를 불렀으면 소재를 주지 않는다. 소재가 있으면 불렀는데도 못 들은 척 딴소리만 하게 되고, 유저가 부르면
     * 그 캐릭터가 답해야 한다는 규칙과 어긋난다. 그때는 유저 말에 엉뚱이 말투로 답한다(COMMENT 프롬프트).
     */
    fun needsEongttungTopic(): Boolean = EmotionType.QUIRKY in selection.characters && EmotionType.QUIRKY !in addressees.present
}
