package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.prompt.ConversationTranscript
import com.nexters.gamss.llm.prompt.TranscriptEntry
import org.springframework.stereotype.Component

/**
 * 저장된 메시지를 읽어 [ConversationTranscript]를 만든다. 무엇을 담을지는 [ConversationTranscript]가 정하고,
 * 여기는 어디서 읽는지만 안다.
 */
@Component
class ConversationTranscriptReader(
    private val messageRepository: MessageRepository,
) {
    /**
     * [beforeMessageId] **직전까지**의 대화를 읽는다. 이번 메시지는 따로 싣기 때문에 빼고, 이번 메시지 뒤에
     * 쌓인 메시지(재시도 사이에 저장된 것 등)는 이번 응답의 맥락이 아니라서 뺀다.
     */
    fun read(
        conversationId: Long,
        beforeMessageId: Long,
        pinned: Collection<EmotionType> = emptySet(),
    ): ConversationTranscript {
        val entries =
            messageRepository
                .findAllByConversationIdOrderByIdAsc(conversationId)
                .filter { it.id < beforeMessageId }
                .map { TranscriptEntry(it.emotionType, it.content) }
        return ConversationTranscript.recent(entries, pinned)
    }
}
