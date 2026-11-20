package com.nexters.gamss.game.controller.dto

import com.nexters.gamss.game.service.RankedGameRecord
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class GameRankingResponse(
    @field:Schema(description = "기록 id", example = "1")
    val id: Long,
    @field:Schema(description = "기록한 회원 닉네임(없으면 null)", example = "속닥이")
    val nickname: String?,
    @field:Schema(description = "점수", example = "87")
    val score: Int,
    @field:Schema(description = "기록 저장 시각(UTC)")
    val playedAt: Instant,
) {
    companion object {
        fun from(ranked: RankedGameRecord): GameRankingResponse =
            GameRankingResponse(ranked.record.id, ranked.nickname, ranked.record.score, ranked.record.createdAt)
    }
}
