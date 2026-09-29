package com.nexters.gamss.llm.prompt

/**
 * [com.nexters.gamss.llm.generation.CommentGenerator.generateReply]가 필요로 하는 컨텍스트를 한데 묶는다.
 * 묶는 이유는 [CommentPromptContext]와 같다. 필드를 늘려도 인터페이스, 구현체(Gemini, Fake),
 * [PromptProvider]가 매번 같이 흔들리지 않게 한다.
 *
 * [characterId]는 프롬프트용 로마자 id(`gippeum` 등, [PromptCharacterId.promptId])다. [transcript]는 유저 답글
 * 직전까지 이 방에서 오간 원문이다([ConversationTranscript]).
 */
data class ReplyPromptContext(
    val diaryContent: String,
    val characterId: String,
    val characterComment: String,
    val userReply: String,
    val transcript: ConversationTranscript = ConversationTranscript.EMPTY,
)
