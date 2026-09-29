package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class VocativeAddresseeResolverTest {
    private val resolver = VocativeAddresseeResolver()

    @ParameterizedTest
    @ValueSource(
        strings = [
            "기쁨아 그게 무슨소리야",
            "기쁨아!! 뭐라고?",
            "근데 기쁨아 그게 무슨 말이야",
            "ㅋㅋ 기쁨아ㅋㅋ 진짜?",
            "기쁨이야 그게 뭐야",
            "기쁨이, 그게 뭐야",
            "기쁨! 들어봐",
            "오늘 힘들었어. 기쁨이야 너는 어때",
        ],
    )
    fun `기쁨이를 부르는 말이면 기쁨이를 부른 것으로 본다`(message: String) {
        assertEquals(listOf(EmotionType.JOY), resolver.resolve(message))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "기쁨이 넘치는 하루였어",
            "오늘은 기쁨이야",
            "기쁨아니 슬픔인가",
            "기쁨과 슬픔이 섞인 날",
            "오늘 너무 기쁨!",
            "오늘 좀 불안해",
            "그건 분노야",
        ],
    )
    fun `이름이 일상어로 쓰였으면 아무도 부르지 않은 것으로 본다`(message: String) {
        // 잘못 잡으면 부르지도 않은 캐릭터 혼자 답한다. 놓치는 것보다 나쁘다.
        assertEquals(emptyList(), resolver.resolve(message))
    }

    @Test
    fun `찾는 말은 호명으로 잡지 않는다`() {
        // 막아둔 캐릭터를 이렇게 찾는 경우는 프롬프트에 막아둔 캐릭터를 항상 싣는 쪽이 맡는다.
        assertEquals(emptyList(), resolver.resolve("슬픔이 어디 갔어?"))
        assertEquals(emptyList(), resolver.resolve("슬픔이는?"))
    }

    @Test
    fun `문장 끝 뒤에 새 문장으로 오는 서술어 기쁨이야는 호명으로 본다`() {
        // 알고 감수하는 오탐이다. 문장 맨 앞의 "기쁨이야 그게 뭐야"와 구분할 수 없다. 이 동작이 바뀌면 KDoc도 고친다.
        assertEquals(listOf(EmotionType.JOY), resolver.resolve("오늘 좋았다. 기쁨이야"))
    }

    @Test
    fun `받침 없는 이름은 문장 맨 앞의 야로 부른다`() {
        assertEquals(listOf(EmotionType.ANGER), resolver.resolve("분노야 들어봐"))
    }

    @Test
    fun `앞선 부르는 말 바로 뒤의 받침 없는 이름도 부른 것으로 본다`() {
        // 실제 LLM 테스트에서 "기쁨아 분노야 둘 다 들어봐"에 기쁨이만 답했다. 분노야가 문장 맨 앞이 아니라 놓쳤다.
        assertEquals(listOf(EmotionType.JOY, EmotionType.ANGER), resolver.resolve("기쁨아 분노야 둘 다 들어봐"))
        assertEquals(listOf(EmotionType.JOY, EmotionType.ANGER), resolver.resolve("기쁨아, 분노! 들어봐"))
    }

    @Test
    fun `여러 캐릭터를 부르면 메시지에 처음 나온 순서대로 돌려준다`() {
        assertEquals(listOf(EmotionType.ANGER, EmotionType.JOY), resolver.resolve("분노야 기쁨아 둘 다 들어봐"))
    }

    @Test
    fun `같은 캐릭터를 여러 번 불러도 한 번만 돌려준다`() {
        assertEquals(listOf(EmotionType.SADNESS), resolver.resolve("슬픔아 슬픔아 어디 갔어"))
    }

    @Test
    fun `여섯 캐릭터 모두 부르는 말로 판정한다`() {
        val message = "기쁨아 슬픔아. 분노야 불안아 까칠아 엉뚱아"

        assertEquals(EmotionType.entries.toList(), resolver.resolve(message))
    }
}
