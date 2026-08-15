package com.nexters.gamss.card.service

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.service.DeletedConversationCleaner
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 채팅방 삭제 시 **카드**를 정리한다(card 패키지 몫).
 *
 * 카드는 그 대화의 결과물이라, 방이 사라졌는데 카드만 살아 있으면 "삭제된 방에 살아 있는 카드"가
 * 남는다. 조회 쿼리들이 `cv.status <> DELETED`로 가려주기 때문에 화면에는 티가 나지 않지만,
 * 반대 방향(카드를 지우면 방까지 지운다 — [CardService.deleteCard])과 데이터가 어긋난다.
 *
 * 이미 지워진 카드는 [CardRepository.softDeleteByConversationIds]가 건너뛰므로 여러 번 호출해도
 * 처음 삭제 시각이 유지된다. 카드 재생성 차단은 그대로다 — `existsByConversationId`가 삭제된
 * 카드도 '존재'로 세기 때문에 같은 방에 카드를 다시 만들 수 없다.
 */
@Component
class DeletedConversationCardCleaner(
    private val cardRepository: CardRepository,
) : DeletedConversationCleaner {
    override fun clean(
        conversationIds: Collection<Long>,
        deletedAt: Instant,
    ) {
        cardRepository.softDeleteByConversationIds(conversationIds, deletedAt)
    }
}
