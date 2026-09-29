package com.nexters.gamss.llm.selection

/** 이번 응답에 누가 몇 번 말할지([selection])와, 그렇게 정한 근거가 된 호명([addressees]). */
data class ResponsePlan(
    val selection: CharacterSelection,
    val addressees: Addressees,
)
