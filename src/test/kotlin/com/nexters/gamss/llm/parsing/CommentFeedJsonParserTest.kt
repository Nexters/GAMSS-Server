package com.nexters.gamss.llm.parsing
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.CommentGenerationFailedException
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CommentFeedJsonParserTest {
    private val parser = CommentFeedJsonParser(JsonMapper())

    @Test
    fun `프롬프트 계약을 따르는 JSON을 CommentFeed로 정확히 변환한다`() {
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

        val feed = parser.parse(text)

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
            parser.parse("이건 JSON이 아니다")
        }
    }

    @Test
    fun `알 수 없는 character_id가 있으면 CommentGenerationFailedException을 던진다`() {
        val text = """{"comments": [{"character_id": "unknown", "text": "?"}], "tikitaka": []}"""

        assertFailsWith<CommentGenerationFailedException> {
            parser.parse(text)
        }
    }
}
