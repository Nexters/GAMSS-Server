package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.prompt.ConversationTranscript
import com.nexters.gamss.llm.prompt.TranscriptEntry
import org.springframework.data.domain.PageRequest
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
     *
     * 대화방 전체를 읽지 않는다. 메시지 본문은 암호문이라 읽을 때마다 복호화가 드는데, 생성은 메시지를 보낼 때마다
     * 일어난다. 구간이 담을 수 있는 최대 개수([ConversationTranscript.MAX_ENTRIES])만 최신순으로 읽고, 그 안에
     * 없는 [pinned] 캐릭터의 마지막 발언만 따로 한 건씩 읽는다.
     */
    fun read(
        conversationId: Long,
        beforeMessageId: Long,
        pinned: Collection<EmotionType> = emptySet(),
    ): ConversationTranscript {
        val recent =
            messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(
                conversationId,
                beforeMessageId,
                PageRequest.of(0, ConversationTranscript.MAX_ENTRIES),
            )
        val pinnedOutsideRecent =
            pinned
                .filter { character -> recent.none { it.emotionType == character } }
                .mapNotNull {
                    messageRepository.findFirstByConversationIdAndEmotionTypeAndIdLessThanOrderByIdDesc(conversationId, it, beforeMessageId)
                }
        val entries = (recent + pinnedOutsideRecent).sortedBy { it.id }.map { TranscriptEntry(it.emotionType, it.content) }
        return ConversationTranscript.recent(entries, pinned)
    }
}
