package com.nexters.gamss.game.service

import com.nexters.gamss.game.domain.GameType
import com.nexters.gamss.game.repository.GameRecordRepository
import com.nexters.gamss.member.service.MemberService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원당 최고 기록 하나로 매기는 랭킹. 닉네임은 페이지 단위로 한 번에 모아 회원 수만큼 조회하지 않는다. */
@Service
class GameRankingService(
    private val gameRecordRepository: GameRecordRepository,
    private val memberService: MemberService,
) {
    @Transactional(readOnly = true)
    fun getRanking(
        gameType: GameType,
        sort: GameRecordSort,
        page: Int,
        size: Int,
    ): Page<RankedGameRecord> {
        val records = gameRecordRepository.findBestPerMember(gameType, PageRequest.of(page, size, sort.sort))
        val nicknames = memberService.getNicknames(records.content.map { it.memberId }.toSet())
        return records.map { RankedGameRecord(it, nicknames[it.memberId]) }
    }
}
