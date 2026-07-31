package com.nexters.gamss.llm.parsing
import com.nexters.gamss.llm.error.CommentGenerationFailedException
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/** [PromptProvider.replyPrompt]가 정의한 출력 형식대로(`{"text": "..."}`) 파싱한다. */
@Component
class ReplyJsonParser(
    private val jsonMapper: JsonMapper,
) {
    fun parse(text: String): String {
        val dto =
            try {
                jsonMapper.readValue(text, ReplyDto::class.java)
            } catch (e: JacksonException) {
                throw CommentGenerationFailedException("LLM 응답 JSON 파싱에 실패했습니다.", e)
            }
        return dto.text
    }

    private data class ReplyDto(
        val text: String,
    )
}
