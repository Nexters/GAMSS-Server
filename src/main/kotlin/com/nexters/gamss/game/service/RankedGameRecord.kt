package com.nexters.gamss.game.service

import com.nexters.gamss.game.domain.GameRecord

/** 랭킹에 보여줄 기록과 그 주인의 닉네임. */
data class RankedGameRecord(
    val record: GameRecord,
    val nickname: String?,
)
