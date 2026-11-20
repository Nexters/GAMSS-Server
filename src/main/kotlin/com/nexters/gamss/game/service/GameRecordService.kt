package com.nexters.gamss.game.service

import com.nexters.gamss.game.domain.GameRecord
import com.nexters.gamss.game.domain.GameType
import com.nexters.gamss.game.repository.GameRecordRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 본인 게임 기록의 저장·조회. */
@Service
class GameRecordService(
    private val gameRecordRepository: GameRecordRepository,
) {
    @Transactional
    fun record(
        memberId: Long,
        gameType: GameType,
        score: Int,
    ): GameRecord = gameRecordRepository.save(GameRecord(memberId, gameType, score))

    @Transactional(readOnly = true)
    fun getMyRecords(
        memberId: Long,
        gameType: GameType,
        sort: GameRecordSort,
        page: Int,
        size: Int,
    ): Page<GameRecord> = gameRecordRepository.findByMemberIdAndGameType(memberId, gameType, PageRequest.of(page, size, sort.sort))
}
