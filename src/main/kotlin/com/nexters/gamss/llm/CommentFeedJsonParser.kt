package com.nexters.gamss.llm

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * [PromptProvider]가 정의한 출력 계약(JSON)을 [CommentFeed]로 변환한다. character_id는 여기서만
 * 문자열로 다루고 [PromptCharacterId]로 번역한다. 특정 LLM SDK에 의존하지 않는다 — 같은 프롬프트
 * 계약을 따르는 응답이면 어떤 구현체가 호출하든 동일하게 파싱된다.
 */
@Component
class CommentFeedJsonParser(
    private val jsonMapper: JsonMapper,
) {
    fun parse(text: String): CommentFeed {
        val dto =
            try {
                jsonMapper.readValue(text, CommentFeedDto::class.java)
            } catch (e: JacksonException) {
                throw CommentGenerationFailedException("LLM 응답 JSON 파싱에 실패했습니다.", e)
            }

        return dto.toDomain()
    }

    private data class CommentFeedDto(
        val comments: List<CommentDraftDto>,
        val tikitaka: List<TikitakaDraftDto>,
    ) {
        fun toDomain(): CommentFeed =
            CommentFeed(
                comments =
                    comments.map {
                        CommentDraft(
                            PromptCharacterId.fromPromptId(it.characterId).emotionType,
                            it.text,
                        )
                    },
                tikitaka =
                    tikitaka.map {
                        TikitakaDraft(
                            characterId = PromptCharacterId.fromPromptId(it.characterId).emotionType,
                            replyTo = PromptCharacterId.fromPromptId(it.replyTo).emotionType,
                            text = it.text,
                        )
                    },
            )
    }

    private data class CommentDraftDto(
        @param:JsonProperty("character_id") val characterId: String,
        val text: String,
    )

    private data class TikitakaDraftDto(
        @param:JsonProperty("character_id") val characterId: String,
        @param:JsonProperty("reply_to") val replyTo: String,
        val text: String,
    )
}
