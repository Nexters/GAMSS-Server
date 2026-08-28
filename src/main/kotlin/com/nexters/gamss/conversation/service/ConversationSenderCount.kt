package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.SenderType

/** 대화방 하나에서 [senderType] 이 보낸 메시지 수. */
data class ConversationSenderCount(
    val conversationId: Long,
    val senderType: SenderType,
    val count: Long,
)
