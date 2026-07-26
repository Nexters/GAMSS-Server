package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.controller.dto.DailyActivityResponse
import com.nexters.gamss.admin.controller.dto.EmotionCountResponse
import com.nexters.gamss.admin.controller.dto.UsageStatsResponse
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * 대시보드 '사용량 / 도입' 집계(SRP: 사용량 지표만 담당). 오늘 KPI는 카운트 쿼리로, 일별 추이는
 * 생성시각 목록을 받아 KST 날짜로 묶어 만든다([KstDashboardDates]).
 */
@Service
class UsageStatsService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val cardRepository: CardRepository,
    private val memberRepository: MemberRepository,
) {
    @Transactional(readOnly = true)
    fun getUsageStats(days: Int): UsageStatsResponse {
        val todayStart = KstDashboardDates.startOfToday()
        val todayEnd = KstDashboardDates.startOfTomorrow()
        val since = KstDashboardDates.sinceDaysAgo(days)
        val weekStart = KstDashboardDates.sinceDaysAgo(WEEK_DAYS)

        val emotionDistribution = cardRepository.countByEmotionSince(since).map(EmotionCountResponse::from)

        val todayUserMessages = messageRepository.countBySenderTypeCreatedBetween(SenderType.USER, todayStart, todayEnd)
        val dau = messageRepository.countActiveMembersBetween(SenderType.USER, todayStart, todayEnd)

        return UsageStatsResponse(
            todayConversations = conversationRepository.countCreatedBetween(todayStart, todayEnd),
            todayUserMessages = todayUserMessages,
            avgMessagesPerUser = averageOrNull(todayUserMessages, dau),
            todayCards = cardRepository.countCreatedBetween(todayStart, todayEnd),
            todaySignups = memberRepository.countCreatedBetween(todayStart, todayEnd),
            dau = dau,
            wau = messageRepository.countActiveMembersBetween(SenderType.USER, weekStart, todayEnd),
            emotionDistribution = emotionDistribution,
            dailyActivity = buildDailyActivity(days, since),
        )
    }

    /** [total] 발화를 [users] 명이 나눠 보낸 1인당 평균. 활동 유저가 없으면 무의미하므로 null. 소수 첫째 자리 반올림. */
    private fun averageOrNull(
        total: Long,
        users: Long,
    ): Double? {
        if (users == 0L) {
            return null
        }
        return (total * 10.0 / users).roundToLong() / 10.0
    }

    private fun buildDailyActivity(
        days: Int,
        since: Instant,
    ): List<DailyActivityResponse> {
        val conversationsByDate = bucketByDate(conversationRepository.findCreatedAtsSince(since))
        val messagesByDate = bucketByDate(messageRepository.findCreatedAtsSince(since))
        val cardsByDate = bucketByDate(cardRepository.findCreatedAtsSince(since))
        return KstDashboardDates.dateAxis(days).map { date ->
            DailyActivityResponse(
                date = date,
                conversations = conversationsByDate[date] ?: 0,
                messages = messagesByDate[date] ?: 0,
                cards = cardsByDate[date] ?: 0,
            )
        }
    }

    private fun bucketByDate(instants: List<Instant>): Map<LocalDate, Long> =
        instants
            .groupingBy { KstDashboardDates.dateOf(it) }
            .eachCount()
            .mapValues { it.value.toLong() }

    companion object {
        private const val WEEK_DAYS = 7
    }
}
