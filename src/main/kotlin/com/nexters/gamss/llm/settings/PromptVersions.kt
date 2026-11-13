package com.nexters.gamss.llm.settings

/** 생성에 쓴 프롬프트 리비전 버전. 저장되지 않은 프롬프트(미리보기)거나 기록 전이면 null. */
data class PromptVersions(
    val common: Int?,
    val type: Int?,
) {
    companion object {
        val UNKNOWN = PromptVersions(null, null)
    }
}
