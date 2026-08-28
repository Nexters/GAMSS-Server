package com.nexters.gamss.card.service

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 카드가 **얼마나 있는지** 센다. 백오피스 대시보드 집계가 쓴다.
 *
 * 사용자 유스케이스([CardService])와 갈라 둔 이유는 바뀌는 이유가 다르기 때문이다. 여기 있는
 * 것들은 "무엇을 보고 싶은가"가 바뀔 때 함께 바뀌고, 카드 자체의 규칙과는 무관하다.
 *
 * 다른 모듈이 [CardRepository] 를 직접 잡지 않게 하는 역할도 겸한다. 인터페이스 프로젝션도 밖으로
 * 내보내지 않는다. 프로젝션은 쿼리 모양에 딸린 것이라 밖으로 새면 쿼리를 바꿀 때 다른 모듈이 깨진다.
 */
@Service
@Transactional(readOnly = true)
class CardStatsService(
    private val cardRepository: CardRepository,
) {
    /** [from, to) 사이(대화 생성시간 기준) 생성된 카드 수. */
    fun countCreatedBetween(
        from: Instant,
        to: Instant,
    ): Long = cardRepository.countCreatedBetween(from, to)

    /** [from] 이후 카드의 생성시각(대화 생성 기준). 일별 추이는 받는 쪽이 묶는다. */
    fun findCreatedAtsSince(from: Instant): List<Instant> = cardRepository.findCreatedAtsSince(from)

    /** [from] 이후 생성된 카드의 감정별 개수. 카드가 하나도 없는 감정은 키 자체가 없다. */
    fun countByEmotionSince(from: Instant): Map<EmotionType, Long> =
        cardRepository.countByEmotionSince(from).associate { it.emotion to it.count }

    /** [conversationIds] 중 카드가 만들어진 대화방 id. */
    fun findConversationIdsWithCard(conversationIds: Collection<Long>): List<Long> = cardRepository.findConversationIdsIn(conversationIds)
}
