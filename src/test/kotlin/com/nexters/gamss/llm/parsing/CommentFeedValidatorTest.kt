package com.nexters.gamss.llm.parsing
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.CommentGenerationFailedException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CommentFeedValidatorTest {
    private val validator = CommentFeedValidator()
    private val characters = listOf(EmotionType.JOY, EmotionType.WARM, EmotionType.GRUMPY)
    private val tikitakaCount = 3

    private fun validFeed() =
        CommentFeed(
            comments = characters.map { CommentDraft(it, "댓글-$it") },
            tikitaka =
                listOf(
                    TikitakaDraft(EmotionType.JOY, EmotionType.WARM, "티키타카1"),
                    TikitakaDraft(EmotionType.WARM, EmotionType.GRUMPY, "티키타카2"),
                    TikitakaDraft(EmotionType.GRUMPY, EmotionType.JOY, "티키타카3"),
                ),
        )

    @Test
    fun `정상 피드는 검증을 통과한다`() {
        validator.validate(validFeed(), characters, tikitakaCount)
    }

    @Test
    fun `comments에 캐릭터가 누락되면 실패한다`() {
        val feed = validFeed().copy(comments = validFeed().comments.dropLast(1))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, tikitakaCount) }
    }

    @Test
    fun `comments에 캐릭터가 중복되면 실패한다`() {
        val feed = validFeed().copy(comments = validFeed().comments + CommentDraft(EmotionType.JOY, "중복"))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, tikitakaCount) }
    }

    @Test
    fun `comments에 요청하지 않은 캐릭터가 섞이면 실패한다`() {
        val feed =
            validFeed().copy(comments = validFeed().comments.dropLast(1) + CommentDraft(EmotionType.ANGER, "외부 캐릭터"))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, tikitakaCount) }
    }

    @Test
    fun `tikitaka replyTo가 1라운드에 없는 캐릭터면 실패한다`() {
        val feed = validFeed().copy(tikitaka = listOf(TikitakaDraft(EmotionType.JOY, EmotionType.ANGER, "잘못된 참조")))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, 1) }
    }

    @Test
    fun `tikitaka에 1라운드에 없는 캐릭터가 존재하면 실패한다`() {
        val feed = validFeed().copy(tikitaka = listOf(TikitakaDraft(EmotionType.ANGER, EmotionType.JOY, "잘못된 참조")))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, 1) }
    }

    @Test
    fun `자기 자신에게 답장하면 실패한다`() {
        val feed = validFeed().copy(tikitaka = listOf(TikitakaDraft(EmotionType.JOY, EmotionType.JOY, "자문자답")))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, 1) }
    }

    @Test
    fun `tikitaka 개수가 요청한 개수와 다르면 실패한다`() {
        val feed = validFeed().copy(tikitaka = listOf(TikitakaDraft(EmotionType.JOY, EmotionType.WARM, "한 개뿐")))

        assertFailsWith<CommentGenerationFailedException> { validator.validate(feed, characters, tikitakaCount) }
    }
}
