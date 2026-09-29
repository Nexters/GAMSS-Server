package com.nexters.gamss.llm.prompt

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * [com.nexters.gamss.llm.generation.CommentGenerator.generateComment]가 필요로 하는 컨텍스트를
 * 한데 묶는다. 파라미터를 하나씩 늘리는 대신 여기 필드를 추가하면, 인터페이스, 구현체(Gemini, Fake),
 * [PromptProvider]가 매번 같이 흔들리지 않는다.
 *
 * [currentConversationSummary]는 프론트가 매 요청마다 압축해 보내는 현재 채팅방의 임시 요약이다
 * (프롬프트에 쓰는 것과 별개로, 자동 종료 배치가 카드 요약으로 쓸 수 있게 최신값을 저장해둔다 -
 * [com.nexters.gamss.conversation.domain.Conversation.updateSummary]).
 * [pastSummaries]는 서버가 같은 회원의 다른 채팅방에서 저장해둔 요약 중
 * 일부를 무작위로 뽑아 넘기는 값이다
 * ([com.nexters.gamss.conversation.repository.ConversationRepository.findRandomPastSummaries]).
 * [transcript]는 이번 메시지 직전까지 이 방에서 오간 원문이다([ConversationTranscript]).
 * [calledCharacters]는 유저가 이번 메시지에서 부른 캐릭터, [calledAbsentCharacters]는 불렀지만 이 방에서 막아둔
 * 캐릭터다([com.nexters.gamss.llm.selection.ResponsePlanner]). [characters]처럼 원시 목록으로 받아, 선택 결과
 * 타입에 기대지 않는다.
 */
data class CommentPromptContext(
    val currentConversationSummary: String?,
    val pastSummaries: PastSummaries,
    val diaryContent: String,
    val characters: List<EmotionType>,
    val tikitakaCount: Int,
    val eongttungTopic: String?,
    val transcript: ConversationTranscript = ConversationTranscript.EMPTY,
    val calledCharacters: List<EmotionType> = emptyList(),
    val calledAbsentCharacters: List<EmotionType> = emptyList(),
)
