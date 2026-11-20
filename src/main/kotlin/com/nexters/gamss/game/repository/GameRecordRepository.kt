package com.nexters.gamss.game.repository

import com.nexters.gamss.game.domain.GameRecord
import com.nexters.gamss.game.domain.GameType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GameRecordRepository : JpaRepository<GameRecord, Long> {
    fun findByMemberIdAndGameType(
        memberId: Long,
        gameType: GameType,
        pageable: Pageable,
    ): Page<GameRecord>

    /**
     * 회원마다 최고 기록 하나씩. 같은 최고 점수가 여러 번이면 먼저 달성한 기록(id 가 작은 쪽)을 고른다.
     * 더 나은 기록이 없는 행만 남기는 방식이라 정렬은 [pageable] 그대로 적용된다.
     */
    @Query(
        value =
            "select r from GameRecord r where r.gameType = :gameType and not exists (" +
                "select 1 from GameRecord o where o.memberId = r.memberId and o.gameType = r.gameType " +
                "and (o.score > r.score or (o.score = r.score and o.id < r.id)))",
        countQuery = "select count(distinct r.memberId) from GameRecord r where r.gameType = :gameType",
    )
    fun findBestPerMember(
        @Param("gameType") gameType: GameType,
        pageable: Pageable,
    ): Page<GameRecord>

    fun deleteByMemberId(memberId: Long)
}
