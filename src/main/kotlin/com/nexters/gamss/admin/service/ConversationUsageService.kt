package com.nexters.gamss.admin.service

import com.nexters.gamss.card.service.AutoCardWindow
import com.nexters.gamss.card.service.CardStatsService
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.service.ConversationStatsService
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.monitoring.service.GenerationLogStatsService
import com.nexters.gamss.notification.service.NotificationLogStatsService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.roundToLong

/**
 * 백오피스 '대화방별 사용량' 페이지 집계. prod, dev 구분 없이 모든 대화방을 최신순으로 페이지네이션하고,
 * 그 페이지에 올라온 대화방 id들에 대해서만 메시지 수, 카드 여부, 토큰 합을 배치로 채운다(N+1 회피).
 */
@Service
class ConversationUsageService(
    private val conversationStatsService: ConversationStatsService,
    private val cardStatsService: CardStatsService,
    private val generationLogStatsService: GenerationLogStatsService,
    private val notificationLogStatsService: NotificationLogStatsService,
    private val geminiPricing: GeminiPricing,
    private val autoCardWindow: AutoCardWindow,
) {
    @Transactional(readOnly = true)
    fun getUsage(pageable: Pageable): Page<ConversationUsage> {
        val page = conversationStatsService.findConversations(pageable)
        val ids = page.content.map { it.id }
        if (ids.isEmpty()) {
            return PageImpl(emptyList(), pageable, page.totalElements)
        }

        val userCounts = mutableMapOf<Long, Long>()
        val characterCounts = mutableMapOf<Long, Long>()
        conversationStatsService.countMessagesBySender(ids).forEach {
            when (it.senderType) {
                SenderType.USER -> userCounts[it.conversationId] = it.count
                SenderType.CHARACTER -> characterCounts[it.conversationId] = it.count
            }
        }

        // 대시보드처럼 생성 로그 행을 받아 대화방별로 그룹핑한다. 토큰(총량과 캐시)은 단순 합,
        // 비용은 모델별 단가라 행마다 요금표로 계산해 더한다(QualityStatsService 와 동일한 costUsd).
        val logsByConversation = generationLogStatsService.findByConversationIds(ids).groupBy { it.conversationId }

        val cardConversationIds = cardStatsService.findConversationIdsWithCard(ids).toSet()

        // 기록이 없는 방은 그 회차에 대상이 아니었다는 뜻이라 맵에 없다.
        val notificationsByConversation = notificationLogStatsService.findOutcomesByConversation(ids)

        val rows =
            page.content.map { conversation ->
                val logs = logsByConversation[conversation.id].orEmpty()
                val rawCost =
                    logs.sumOf {
                        geminiPricing.costUsd(
                            it.model,
                            it.inputTokens ?: 0,
                            it.cachedTokens ?: 0,
                            it.outputTokens ?: 0,
                        )
                    }
                ConversationUsage(
                    conversationId = conversation.id,
                    memberId = conversation.memberId,
                    title = conversation.title?.value,
                    status = conversation.status,
                    createdAt = conversation.createdAt,
                    userMessageCount = userCounts[conversation.id] ?: 0,
                    characterMessageCount = characterCounts[conversation.id] ?: 0,
                    cardCreated = conversation.id in cardConversationIds,
                    totalTokens = logs.sumOf { (it.usedTokens ?: 0).toLong() },
                    cachedTokens = logs.sumOf { (it.cachedTokens ?: 0).toLong() },
                    estimatedCostUsd = (rawCost * 10000).roundToLong() / 10000.0,
                    reminderNotification = notificationsByConversation[conversation.id]?.reminder,
                    cardNotification = notificationsByConversation[conversation.id]?.cardCreated,
                    // 리마인더 시각과 하루 경계는 배치가 가진 값이다. 화면이 그 시각을 다시 적어
                    // 두면 경계를 옮겼을 때 표만 옛 값으로 남으므로, 판정을 여기서 끝낸다.
                    createdInReminderGap = autoCardWindow.isCreatedInReminderGap(conversation.createdAt),
                )
            }
        return PageImpl(rows, pageable, page.totalElements)
    }
}
