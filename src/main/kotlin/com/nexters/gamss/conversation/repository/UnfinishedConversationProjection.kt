package com.nexters.gamss.conversation.repository

/** 아직 종료되지 않은 대화방 하나와 그 주인. 미종료 리마인더가 대상과 기록 대상을 함께 알아야 해서 둘을 같이 받는다. */
interface UnfinishedConversationProjection {
    val conversationId: Long
    val memberId: Long
}
