package com.nexters.gamss.llm

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component

/**
 * `responseSchema`는 JSON 모양만 강제한다. 캐릭터 참조가 실재하는지, 자문자답은 아닌지 같은
 * 의미 규칙은 서버 코드로 검증해야 한다. 위반은 재시도 대상이므로
 * [CommentGenerationFailedException]으로 던진다.
 */
@Component
class CommentFeedValidator {
    fun validate(
        feed: CommentFeed,
        expectedCharacters: List<EmotionType>,
        expectedTikitakaCount: Int,
    ) {
        val expectedSet = expectedCharacters.toSet()
        val commentCharacterIds = feed.comments.map { it.characterId }

        fail(commentCharacterIds.toSet() != expectedSet || commentCharacterIds.size != expectedSet.size) {
            "comments의 캐릭터 구성이 요청한 캐릭터 목록과 다릅니다. expected=$expectedSet, actual=$commentCharacterIds"
        }

        fail(feed.comments.any { it.text.isBlank() }) { "댓글 내용이 비어 있습니다." }
        fail(feed.tikitaka.size != expectedTikitakaCount) {
            "tikitaka 개수가 요청과 다릅니다. expected=$expectedTikitakaCount, actual=${feed.tikitaka.size}"
        }
        feed.tikitaka.forEach { tikitaka ->
            fail(tikitaka.replyTo !in expectedSet) {
                "tikitaka.replyTo가 실재하는 1라운드 캐릭터가 아닙니다: ${tikitaka.replyTo}"
            }
            fail(tikitaka.characterId !in expectedSet) {
                "tikitaka에 comments에 존재하지 않는 캐릭터 ${tikitaka.characterId} 이/가 있습니다."
            }
            fail(tikitaka.characterId == tikitaka.replyTo) {
                "캐릭터가 자기 자신에게 답장할 수 없습니다: ${tikitaka.characterId}"
            }
            fail(tikitaka.text.isBlank()) { "티키타카 내용이 비어 있습니다." }
        }
    }

    private inline fun fail(
        condition: Boolean,
        lazyMessage: () -> String,
    ) {
        if (condition) throw CommentGenerationFailedException(lazyMessage())
    }
}
