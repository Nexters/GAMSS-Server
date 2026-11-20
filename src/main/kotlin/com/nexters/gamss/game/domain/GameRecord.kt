package com.nexters.gamss.game.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 미니게임 한 판의 결과. 점수 유효성은 게임마다 다르므로 [GameType] 에 묻는다. */
@Entity
@Table(name = "game_records")
class GameRecord(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 30)
    val gameType: GameType,
    @Column(name = "score", nullable = false)
    val score: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    init {
        if (!gameType.accepts(score)) {
            throw BusinessException(ErrorCode.INVALID_GAME_SCORE)
        }
    }
}
