package com.nexters.gamss.game.service

import com.nexters.gamss.game.domain.GameRecord
import com.nexters.gamss.game.domain.GameType
import com.nexters.gamss.game.repository.GameRecordRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.service.MemberService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 본인 게임 기록의 저장·조회. */
@Service
class GameRecordService(
    private val gameRecordRepository: GameRecordRepository,
    private val memberService: MemberService,
) {
    // 탈퇴 후에도 accessToken 은 만료 전까지 유효하다. 막지 않으면 탈퇴 정리 이후의 기록이 랭킹에 남는다.
    @Transactional
    fun record(
        memberId: Long,
        gameType: GameType,
        score: Int,
    ): GameRecord {
        if (memberService.getById(memberId).isWithdrawn()) {
            throw BusinessException(ErrorCode.WITHDRAWN_MEMBER)
        }
        return gameRecordRepository.save(GameRecord(memberId, gameType, score))
    }

    @Transactional(readOnly = true)
    fun getMyRecords(
        memberId: Long,
        gameType: GameType,
        sort: GameRecordSort,
        page: Int,
        size: Int,
    ): Page<GameRecord> = gameRecordRepository.findByMemberIdAndGameType(memberId, gameType, PageRequest.of(page, size, sort.sort))
}
