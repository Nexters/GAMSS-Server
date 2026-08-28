package com.nexters.gamss.conversation.service

/**
 * 아직 종료되지 않은 대화방 하나와 그 주인.
 *
 * 알림은 회원당 한 번만 나가지만(보내는 쪽이 회원 id 를 추린다), 어떤 방 때문에 대상이 됐는지를
 * 발송 이력에 남겨야 백오피스가 대화방별로 보여줄 수 있다
 * ([com.nexters.gamss.notification.domain.NotificationLog]).
 */
data class UnfinishedConversation(
    val conversationId: Long,
    val memberId: Long,
)
