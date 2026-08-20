package com.nexters.gamss.llm.generation

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.parsing.CommentFeed
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenUsageAccumulatorTest {
    @Test
    fun `성공 시도와 실패 시도의 토큰을 모두 누적한다`() {
        val acc = TokenUsageAccumulator()

        // 1차 실패(검증/파싱 실패라 토큰이 예외에 실림)
        acc.addFailed(CommentGenerationFailedException("검증 실패", usedTokens = 100, cachedTokens = 10, inputTokens = 80, outputTokens = 20))
        // 2차 성공
        acc.add(
            CommentGenerationOutput(
                CommentFeed(emptyList(), emptyList()),
                usedTokens = 200,
                cachedTokens = 30,
                inputTokens = 150,
                outputTokens = 50,
            ),
        )

        assertEquals(300, acc.used)
        assertEquals(40, acc.cached)
        assertEquals(230, acc.input)
        assertEquals(70, acc.output)
    }

    @Test
    fun `카드 경로의 성공 시도와 실패 시도도 같은 방식으로 누적한다`() {
        val acc = TokenUsageAccumulator()

        // 1차 실패(파싱 실패라 토큰이 예외에 실림)
        acc.addFailed(CardGenerationFailedException("파싱 실패", usedTokens = 100, cachedTokens = 10, inputTokens = 80, outputTokens = 20))
        // 2차 성공
        acc.add(EmotionExtractionOutput(EmotionType.JOY, usedTokens = 200, cachedTokens = 30, inputTokens = 150, outputTokens = 50))
        // 이어지는 한 줄 생성
        acc.add(CardMessageOutput("오늘은 좋은 일이 있었어요", usedTokens = 50, cachedTokens = 5, inputTokens = 40, outputTokens = 10))

        assertEquals(350, acc.used)
        assertEquals(45, acc.cached)
        assertEquals(270, acc.input)
        assertEquals(80, acc.output)
    }

    @Test
    fun `호출 자체가 실패해 토큰이 없으면(null) 0으로 더한다`() {
        val acc = TokenUsageAccumulator()

        acc.addFailed(CommentGenerationFailedException("LLM 호출 실패")) // 토큰 전부 null

        assertEquals(0, acc.used)
        assertEquals(0, acc.cached)
        assertEquals(0, acc.input)
        assertEquals(0, acc.output)
    }
}
