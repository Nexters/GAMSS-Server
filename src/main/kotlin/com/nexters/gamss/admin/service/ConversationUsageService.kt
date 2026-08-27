package com.nexters.gamss.admin.service

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.llm.config.GeminiPricing
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.notification.domain.NotificationType
import com.nexters.gamss.notification.repository.NotificationLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.roundToLong

/**
 * 백오피스 '대화방별 사용량' 페이지 집계. prod·dev 구분 없이 모든 대화방을 최신순으로 페이지네이션하고,
 * 그 페이지에 올라온 대화방 id들에 대해서만 메시지 수·카드 여부·토큰 합을 배치로 채운다(N+1 회피).
 */
@Service
class ConversationUsageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val cardRepository: CardRepository,
    private val generationLogRepository: GenerationLogRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val geminiPricing: GeminiPricing,
) {
    @Transactional(readOnly = true)
    fun getUsage(pageable: Pageable): Page<ConversationUsage> {
        val page = conversationRepository.findAll(pageable)
        val ids = page.content.map { it.id }
        if (ids.isEmpty()) {
            return PageImpl(emptyList(), pageable, page.totalElements)
        }

        val userCounts = mutableMapOf<Long, Long>()
        val characterCounts = mutableMapOf<Long, Long>()
        messageRepository.countBySenderForConversations(ids).forEach {
            when (it.senderType) {
                SenderType.USER -> userCounts[it.conversationId] = it.count
                SenderType.CHARACTER -> characterCounts[it.conversationId] = it.count
            }
        }

        // 대시보드처럼 생성 로그 행을 받아 대화방별로 그룹핑한다. 토큰(총량·캐시)은 단순 합,
        // 비용은 모델별 단가라 행마다 요금표로 계산해 더한다(QualityStatsService 와 동일한 costUsd).
        val logsByConversation = generationLogRepository.findByConversationIdIn(ids).groupBy { it.conversationId }

        val cardConversationIds = cardRepository.findConversationIdsIn(ids).toSet()

        // (대화방, 알림 종류) 짝마다 마지막 결과 하나. 기록이 없으면 그 회차에 대상이 아니었다는 뜻이다.
        val notificationsByConversation =
            notificationLogRepository
                .findLatestByConversationIdIn(ids)
                .associateBy { it.conversationId to it.type }

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
                    reminderNotification =
                        notificationsByConversation[conversation.id to NotificationType.UNFINISHED_REMINDER]?.outcome,
                    cardNotification =
                        notificationsByConversation[conversation.id to NotificationType.CARD_CREATED]?.outcome,
                )
            }
        return PageImpl(rows, pageable, page.totalElements)
    }
}
