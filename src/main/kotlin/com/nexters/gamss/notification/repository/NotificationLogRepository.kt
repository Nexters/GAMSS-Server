package com.nexters.gamss.notification.repository

import com.nexters.gamss.notification.domain.NotificationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    /**
     * [conversationIds] 각각의 **종류별 가장 최근** 기록. 백오피스 대화방별 사용량 한 페이지분을
     * 한 번에 읽는다.
     *
     * 재발송이 생기면 같은 (방, 종류)에 줄이 여러 개 쌓이는데, 표가 보여줄 것은 마지막 결과다.
     * 그래서 같은 짝의 최대 id 만 남긴다(created_at 이 아니라 id 로 고르는 이유는 같은 배치 회차의
     * 두 줄이 마이크로초까지 같을 수 있어서다).
     */
    @Query(
        "select n from NotificationLog n where n.id in (" +
            "select max(m.id) from NotificationLog m " +
            "where m.conversationId in :conversationIds group by m.conversationId, m.type)",
    )
    fun findLatestByConversationIdIn(
        @Param("conversationIds") conversationIds: Collection<Long>,
    ): List<NotificationLog>
}
