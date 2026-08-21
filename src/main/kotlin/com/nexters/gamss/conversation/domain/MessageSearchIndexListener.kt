package com.nexters.gamss.conversation.domain

import com.nexters.gamss.global.crypto.BlindIndexer
import jakarta.persistence.PrePersist
import org.springframework.stereotype.Component

/**
 * 메시지를 저장하기 직전에 검색용 블라인드 인덱스를 채운다.
 *
 * 서비스에서 채우지 않고 리스너로 둔 이유는 저장 경로가 여러 곳이기 때문이다
 * ([com.nexters.gamss.conversation.service.ConversationService]의 사용자 메시지,
 * [com.nexters.gamss.conversation.service.CommentPersistenceService]의 캐릭터 댓글·티키타카).
 * 한 곳이라도 빠뜨리면 저장은 됐는데 검색만 안 되는 메시지가 조용히 생긴다.
 *
 * `@PreUpdate` 는 두지 않는다 — 메시지 본문은 생성 후 바뀌지 않는 값이라(수정 API가 없다) 갱신
 * 시점에 다시 만들 일이 없다. 본문을 고칠 수 있게 되면 그때 함께 열어야 한다.
 */
@Component
class MessageSearchIndexListener(
    private val indexer: BlindIndexer,
) {
    @PrePersist
    fun fillSearchIndex(message: Message) {
        message.applySearchIndex(indexer)
    }
}
