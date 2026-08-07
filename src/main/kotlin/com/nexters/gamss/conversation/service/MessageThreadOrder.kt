package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType

/**
 * 메시지 목록(시간순, id ASC)을 받아 캐릭터끼리 주고받는 티키타카를 자신이 답장한 댓글 바로 뒤로
 * 옮겨, 클라이언트가 받는 목록이 실제 대화 스레드처럼 읽히게 재배열한다. 티키타카는 "캐릭터가 캐릭터
 * 댓글에 답장한 메시지"로만 판정한다 — 유저가 캐릭터 댓글에 단 답장이나, 유저 답글에 캐릭터가 다시
 * 응답한 메시지처럼 둘 중 하나라도 캐릭터가 아닌 경우는 건드리지 않는다 — 이미 시간순으로 답장 대상
 * 바로 다음에 저장되기 때문이다.
 *
 * [CommentPersistenceService.saveFeed]가 저장 직후 응답에 적용하던 재배치 규칙을, 이후 재조회
 * 경로(채팅방 메시지 목록 조회, 이미 완료된 생성 재시도)에도 똑같이 적용하기 위한 순수 함수다.
 */
internal object MessageThreadOrder {
    fun reorderTikitakaAfterTarget(messages: List<Message>): List<Message> {
        val messageById = messages.associateBy { it.id }
        val tikitakaByTarget =
            messages
                .filter {
                    it.senderType == SenderType.CHARACTER &&
                        it.repliesToMessageId != null &&
                        messageById[it.repliesToMessageId]?.senderType == SenderType.CHARACTER
                }.groupBy { it.repliesToMessageId }
        val tikitakaIds =
            tikitakaByTarget.values
                .flatten()
                .map { it.id }
                .toSet()
        return messages
            .filterNot { it.id in tikitakaIds }
            .flatMap { message -> listOf(message) + tikitakaByTarget[message.id].orEmpty() }
    }
}
