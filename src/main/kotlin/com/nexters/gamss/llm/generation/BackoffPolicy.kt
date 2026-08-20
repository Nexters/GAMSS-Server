package com.nexters.gamss.llm.generation

/**
 * 재시도 사이에 얼마나 기다릴지 정한다.
 *
 * 시도 번호뿐 아니라 **무엇 때문에 실패했는지**도 받는다 — 대기 시간이 실패 종류로 갈리기 때문이다.
 * 응답 검증 실패는 Gemini 가 멀쩡하고 형식만 틀린 것이라 기다릴 이유가 없고, 429 쿼터 초과는 짧게
 * 기다려봐야 그대로 또 429 라 다른 간격이 필요하다(#162).
 */
fun interface BackoffPolicy {
    /** [attempt]번째 시도가 [error]로 실패했을 때 다음 시도까지 기다릴 시간. 0 이하면 즉시 재시도한다. */
    fun delayMillisFor(
        attempt: Int,
        error: Exception,
    ): Long

    companion object {
        /** 실패 종류·시도 번호와 무관하게 같은 간격으로 기다린다. */
        fun fixed(millis: Long) = BackoffPolicy { _, _ -> millis }
    }
}
