package com.nexters.gamss.conversation.service

import java.time.Instant

/**
 * 채팅방 삭제에 딸린 자원 정리 확장점. 채팅방 밖(다른 패키지)에 있는 자원의 정리는 그 자원을
 * 소유한 패키지가 이 인터페이스를 구현해 맡는다([com.nexters.gamss.member.service.WithdrawnMemberCleaner]와
 * 같은 형태).
 *
 * 계약을 conversation이 소유하고 구현을 바깥이 제공하는 형태(DIP)라, conversation 패키지는 무엇이
 * 정리되는지 몰라도 되고 의존은 `구현 패키지 -> conversation` 한 방향으로 유지된다 — 카드 삭제가
 * 이미 반대 방향(`card -> conversation`)으로 채팅방을 지우고 있어, 여기서 conversation이 card를
 * 직접 부르면 두 패키지가 서로를 참조하게 된다.
 */
interface DeletedConversationCleaner {
    /**
     * [conversationIds] 채팅방이 삭제되어 더는 살아 있을 이유가 없는 자원을 지운다.
     *
     * 채팅방 삭제와 **같은 트랜잭션**에서, 채팅방 행을 잡기 **전에** 실행된다 — 반대 방향인 카드
     * 삭제가 카드 → 채팅방 순으로 행을 잡으므로 여기서 반대로 잡으면 동시 요청이 데드락으로 죽는다
     * ([com.nexters.gamss.card.service.CardService.deleteCardsWithConversations] KDoc의 잠금 순서
     * 설명). 먼저 지우더라도 뒤이은 채팅방 삭제가 실패하면 같은 트랜잭션이라 함께 롤백된다.
     *
     * [deletedAt]은 한 번의 요청에서 정리되는 모든 자원이 공유한다 — 함께 사라진 자원들이 이력에서
     * 서로 다른 요청처럼 보이지 않아야 한다.
     */
    fun clean(
        conversationIds: Collection<Long>,
        deletedAt: Instant,
    )
}
