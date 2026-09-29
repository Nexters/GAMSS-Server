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
    fun `받침 없는 이름은 문장 맨 앞의 야로 부른다`() {
        assertEquals(listOf(EmotionType.ANGER), resolver.resolve("분노야 들어봐"))
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
