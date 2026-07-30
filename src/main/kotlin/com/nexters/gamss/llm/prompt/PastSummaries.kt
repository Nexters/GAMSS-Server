package com.nexters.gamss.llm.prompt

/**
 * 댓글 생성 프롬프트에 들어갈 과거 대화 요약들. 개행 등을 정규화하고 빈 문자열을 걸러낸 결과만
 * 담는다는 불변식을 [of]가 보장한다 — [PromptProvider]는 이미 정규화된 [lines]만 그대로 쓰면 된다.
 */
data class PastSummaries private constructor(
    val lines: List<String>,
) {
    companion object {
        fun of(raw: List<String>): PastSummaries = PastSummaries(raw.map { it.normalizeForPrompt() }.filter { it.isNotBlank() })
    }
}

/** 연속 공백(개행 포함)을 스페이스 하나로 뭉갠다 — 신뢰할 수 없는 입력이 프롬프트 섹션 구조를 흉내 내지 못하게 한다. */
internal fun String.normalizeForPrompt(): String = replace(Regex("\\s+"), " ").trim()
