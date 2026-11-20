package com.nexters.gamss.game.controller.dto

import com.nexters.gamss.game.domain.GameRecord
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class GameRecordResponse(
    @field:Schema(description = "기록 id", example = "1")
    val id: Long,
    @field:Schema(description = "점수", example = "87")
    val score: Int,
    @field:Schema(description = "기록 저장 시각(UTC)")
    val playedAt: Instant,
) {
    companion object {
        fun from(record: GameRecord): GameRecordResponse = GameRecordResponse(record.id, record.score, record.createdAt)
    }
}
