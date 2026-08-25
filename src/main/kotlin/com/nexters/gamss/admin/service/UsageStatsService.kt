package com.nexters.gamss.admin.service

import com.nexters.gamss.card.service.CardReadService
import com.nexters.gamss.conversation.service.ConversationReadService
import com.nexters.gamss.member.service.MemberService
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
    private val conversationReadService: ConversationReadService,
    private val cardReadService: CardReadService,
    private val memberService: MemberService,
) {
    @Transactional(readOnly = true)
    fun getUsageStats(days: Int): UsageStats {
        val today = KstDashboardDates.today()
        val todayStart = KstDashboardDates.startOfDay(today)
        val todayEnd = KstDashboardDates.startOfNextDay(today)
        val since = KstDashboardDates.daysAgoStart(today, days)
        val weekStart = KstDashboardDates.daysAgoStart(today, WEEK_DAYS)

        val emotionDistribution =
            cardReadService.countByEmotionSince(since).map { (emotion, count) -> EmotionCount(emotion, count) }

        val todayUserMessages = conversationReadService.countUserMessagesCreatedBetween(todayStart, todayEnd)
        val dau = conversationReadService.countActiveMembersBetween(todayStart, todayEnd)

        return UsageStats(
            todayConversations = conversationReadService.countConversationsCreatedBetween(todayStart, todayEnd),
            todayUserMessages = todayUserMessages,
            avgMessagesPerUser = averageOrNull(todayUserMessages, dau),
            todayCards = cardReadService.countCreatedBetween(todayStart, todayEnd),
            todaySignups = memberService.countSignupsBetween(todayStart, todayEnd),
            dau = dau,
            wau = conversationReadService.countActiveMembersBetween(weekStart, todayEnd),
            emotionDistribution = emotionDistribution,
            dailyActivity = buildDailyActivity(today, days, since),
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
        today: LocalDate,
        days: Int,
        since: Instant,
    ): List<DailyActivity> {
        val conversationsByDate = bucketByDate(conversationReadService.findConversationCreatedAtsSince(since))
        val messagesByDate = bucketByDate(conversationReadService.findMessageCreatedAtsSince(since))
        val cardsByDate = bucketByDate(cardReadService.findCreatedAtsSince(since))
        return KstDashboardDates.dateAxis(today, days).map { date ->
            DailyActivity(
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
