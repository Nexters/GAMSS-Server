package com.nexters.gamss.conversation.domain

/** 채팅방을 누가 종료시켰는지. 과거 방은 값이 없을 수 있다(NULL = 알 수 없음, 백필 불가). */
enum class ConversationEndedBy {
    USER,
    AUTO_BATCH,
}
