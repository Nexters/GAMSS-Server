package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message

/**
 * 채팅방 하나와 그 메시지 전부. 방을 열 때 클라이언트가 한 번에 필요로 하는 묶음이다
 * ([ConversationService.getConversationDetail]).
 */
data class ConversationDetail(
    val conversation: Conversation,
    val messages: List<Message>,
)
