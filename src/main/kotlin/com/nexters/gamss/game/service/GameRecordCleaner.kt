package com.nexters.gamss.game.service

import com.nexters.gamss.game.repository.GameRecordRepository
import com.nexters.gamss.member.service.WithdrawnMemberCleaner
import org.springframework.stereotype.Component

/** 회원 탈퇴 시 게임 기록을 지운다. 남겨두면 탈퇴한 사람이 랭킹에 계속 보인다. */
@Component
class GameRecordCleaner(
    private val gameRecordRepository: GameRecordRepository,
) : WithdrawnMemberCleaner {
    override fun clean(memberId: Long) {
        gameRecordRepository.deleteByMemberId(memberId)
    }
}
