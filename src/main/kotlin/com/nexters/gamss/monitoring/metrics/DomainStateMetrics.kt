package com.nexters.gamss.monitoring.metrics

import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * "지금 이 순간 얼마나 쌓여 있는가"를 게이지로 노출한다(SRP: 상태 스냅샷만 담당).
 *
 * 백오피스 대시보드가 이미 다루는 일별 집계(대화·카드·가입 수, LLM 비용·성공률)와는 일부러 겹치지
 * 않게 골랐다. 여기 있는 값들은 **시계열로 봐야 의미가 생기고 임계치 알림을 걸 수 있는** 것들이다 —
 * 예를 들어 PENDING 5건은 정상이지만, 5건이 30분째 줄지 않으면 생성 경로가 막힌 것이다.
 *
 * 게이지는 스크레이프(15초)마다 DB 를 때리는 대신 주기적으로 뜬 스냅샷을 읽는다. 스크레이프 경로에
 * DB 조회를 두면 DB 가 느려질 때 메트릭 수집까지 함께 막혀, 정작 장애 순간에 관측을 잃는다.
 * 갱신 실패도 삼킨다 — 모니터링이 서비스를 흔들면 안 되고, 값이 멈추면 그 자체가 신호가 된다.
 */
@Component
class DomainStateMetrics(
    registry: MeterRegistry,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeConversations = AtomicLong(0)
    private val pendingComments = AtomicLong(0)
    private val pendingCardGenerations = AtomicLong(0)
    private val failedCardGenerations = AtomicLong(0)

    init {
        register(registry, "gamss.conversation.active", "진행 중(ACTIVE) 대화방 수", activeConversations)
        register(registry, "gamss.comment.generation.pending", "댓글 생성 선점(PENDING) 상태 메시지 수", pendingComments)
        register(registry, "gamss.card.generation.pending", "카드 생성 선점(PENDING) 상태 대화방 수", pendingCardGenerations)
        register(registry, "gamss.card.generation.failed", "카드 생성 실패(FAILED)로 남은 대화방 수", failedCardGenerations)
    }

    @Scheduled(fixedDelay = REFRESH_INTERVAL_MILLIS)
    fun refresh() {
        runCatching {
            activeConversations.set(conversationRepository.countByStatus(ConversationStatus.ACTIVE))
            pendingComments.set(messageRepository.countByCommentStatus(CommentStatus.PENDING))
            pendingCardGenerations.set(conversationRepository.countByCardGenerationStatus(CardGenerationStatus.PENDING))
            failedCardGenerations.set(conversationRepository.countByCardGenerationStatus(CardGenerationStatus.FAILED))
        }.onFailure { log.warn("도메인 상태 메트릭 갱신 실패(무시)", it) }
    }

    private fun register(
        registry: MeterRegistry,
        name: String,
        description: String,
        holder: AtomicLong,
    ) {
        Gauge
            .builder(name, holder) { it.get().toDouble() }
            .description(description)
            .register(registry)
    }

    companion object {
        /**
         * 갱신 주기. 스크레이프 간격(15초)보다 길어 같은 값이 몇 번 반복돼 보이지만, 적체는 분 단위로
         * 판단하는 값이라 해상도가 아니라 DB 부하를 아끼는 쪽이 이득이다.
         */
        private const val REFRESH_INTERVAL_MILLIS = 60_000L
    }
}
