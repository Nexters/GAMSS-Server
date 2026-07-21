package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiCommentGeneratorTest {
    private val generator =
        GeminiCommentGenerator(
            properties = GeminiProperties(apiKey = "test-key", model = "test-model"),
            jsonMapper = JsonMapper(),
        )

    @Test
    fun `Gemini가 내려준 JSON을 CommentFeed로 정확히 변환한다`() {
        val text =
            """
            {
              "comments": [
                {"character_id": "gippeum", "text": "완전 잘했다!"},
                {"character_id": "kkachil", "text": "그래봤자지."}
              ],
              "tikitaka": [
                {"character_id": "kkachil", "reply_to": "gippeum", "text": "니가 더 오버지."}
              ]
            }
            """.trimIndent()

        val feed = generator.parseFeed(text)

        assertEquals(
            CommentFeed(
                comments =
                    listOf(
                        CommentDraft(EmotionType.JOY, "완전 잘했다!"),
                        CommentDraft(EmotionType.GRUMPY, "그래봤자지."),
                    ),
                tikitaka =
                    listOf(
                        TikitakaDraft(EmotionType.GRUMPY, EmotionType.JOY, "니가 더 오버지."),
                    ),
            ),
            feed,
        )
    }

    @Test
    fun `JSON 형식이 깨지면 CommentGenerationFailedException을 던진다`() {
        assertFailsWith<CommentGenerationFailedException> {
            generator.parseFeed("이건 JSON이 아니다")
        }
    }

    @Test
    fun `알 수 없는 character_id가 있으면 CommentGenerationFailedException을 던진다`() {
        val text = """{"comments": [{"character_id": "unknown", "text": "?"}], "tikitaka": []}"""

        assertFailsWith<CommentGenerationFailedException> {
            generator.parseFeed(text)
        }
    }
}
