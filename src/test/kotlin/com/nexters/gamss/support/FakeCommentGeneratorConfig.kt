package com.nexters.gamss.support

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import com.nexters.gamss.llm.generation.CommentGenerationOutput
import com.nexters.gamss.llm.generation.CommentGenerator
import com.nexters.gamss.llm.generation.ReplyGenerationOutput
import com.nexters.gamss.llm.parsing.CommentDraft
import com.nexters.gamss.llm.parsing.CommentFeed
import com.nexters.gamss.llm.parsing.TikitakaDraft
import com.nexters.gamss.llm.prompt.PastSummaries
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 실제 Gemini 호출([com.nexters.gamss.llm.generation.GeminiCommentGenerator]) 대신
 * 쓰는 가짜 구현. [CharacterSelector]가 서버에서 무작위로 고른 캐릭터·티키타카 개수를 그대로
 * 되돌려주므로 [com.nexters.gamss.llm.parsing.CommentFeedValidator] 검증을 항상 통과한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeCommentGeneratorConfig {
    @Bean
    @Primary
    fun commentGenerator(): CommentGenerator = FakeCommentGenerator()
}

class FakeCommentGenerator : CommentGenerator {
    /** true로 바꾸면 매 호출이 재시도 소진까지 실패한다(FAILED 경로 검증용). */
    var shouldFail = false

    override fun generateComment(
        currentConversationSummary: String?,
        pastSummaries: PastSummaries,
        diaryContent: String,
        characters: List<EmotionType>,
        tikitakaCount: Int,
        eongttungTopic: String?,
    ): CommentGenerationOutput {
        if (shouldFail) throw CommentGenerationFailedException("테스트 강제 실패")
        val comments = characters.map { CommentDraft(it, "댓글-$it") }
        val tikitaka =
            (0 until tikitakaCount).map { i ->
                val from = characters[i % characters.size]
                val to = characters[(i + 1) % characters.size]
                TikitakaDraft(from, to, "티키타카-$i")
            }
        return CommentGenerationOutput(CommentFeed(comments, tikitaka), usedTokens = 10, cachedTokens = 0)
    }

    override fun generateReply(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
    ): ReplyGenerationOutput {
        if (shouldFail) throw CommentGenerationFailedException("테스트 강제 실패")
        return ReplyGenerationOutput("재응답 텍스트", usedTokens = 5, cachedTokens = 0)
    }
}
