package com.nexters.gamss.admin.service

import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

        val totalTokens = mutableMapOf<Long, Long>()
        val cachedTokens = mutableMapOf<Long, Long>()
        generationLogRepository.sumTokensForConversations(ids).forEach {
            totalTokens[it.conversationId] = it.totalTokens
            cachedTokens[it.conversationId] = it.cachedTokens
        }

        val cardConversationIds = cardRepository.findConversationIdsIn(ids).toSet()

        val rows =
            page.content.map { conversation ->
                ConversationUsage(
                    conversationId = conversation.id,
                    memberId = conversation.memberId,
                    title = conversation.title?.value,
                    status = conversation.status,
                    createdAt = conversation.createdAt,
                    userMessageCount = userCounts[conversation.id] ?: 0,
                    characterMessageCount = characterCounts[conversation.id] ?: 0,
                    cardCreated = conversation.id in cardConversationIds,
                    totalTokens = totalTokens[conversation.id] ?: 0,
                    cachedTokens = cachedTokens[conversation.id] ?: 0,
                )
            }
        return PageImpl(rows, pageable, page.totalElements)
    }
}
