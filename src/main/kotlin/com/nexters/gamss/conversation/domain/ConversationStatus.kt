package com.nexters.gamss.conversation.domain

/**
 * 채팅방 상태. ACTIVE(대화 가능) → ENDED(종료). 종료된 방에는 사용자 메시지를 추가할 수 없다.
 */
enum class ConversationStatus {
    ACTIVE,
    ENDED,
}
