package com.nexters.gamss.llm.generation

/**
 * 재시도 사이의 대기를 담당한다. 인터페이스로 둔 이유는 하나뿐이다 — 테스트가 실제로 잠들지 않게
 * 갈아끼우기 위해서다. 백오프가 1.5초로 늘어나는 순간(#162) 재시도 테스트마다 그만큼 느려진다.
 */
fun interface RetrySleeper {
    fun sleep(millis: Long)

    companion object {
        /** 운영 기본 구현. Web MVC라 여기서 자는 스레드가 곧 요청 스레드다. */
        val THREAD = RetrySleeper { Thread.sleep(it) }
    }
}
