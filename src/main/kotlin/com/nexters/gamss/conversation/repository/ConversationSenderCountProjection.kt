package com.nexters.gamss.conversation.repository

import com.nexters.gamss.conversation.domain.SenderType

/** 대화방·발신주체별 메시지 수 집계 결과(백오피스 대화방 사용량). Spring Data 인터페이스 프로젝션. */
interface ConversationSenderCountProjection {
    val conversationId: Long
    val senderType: SenderType
    val count: Long
}
