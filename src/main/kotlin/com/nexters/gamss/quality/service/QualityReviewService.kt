package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.service.ResponseThreadService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ConversationViewLog
import com.nexters.gamss.quality.domain.LabelingCandidate
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseFilter
import com.nexters.gamss.quality.domain.ResponseHumanVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.repository.ConversationViewLogRepository
import com.nexters.gamss.quality.repository.ResponseAiVerdictRepository
import com.nexters.gamss.quality.repository.ResponseHumanVerdictRepository
import com.nexters.gamss.quality.repository.ResponseQualityQueryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** 판정을 읽고 사람 판정을 남긴다. */
@Service
class QualityReviewService(
    private val aiVerdictRepository: ResponseAiVerdictRepository,
    private val humanVerdictRepository: ResponseHumanVerdictRepository,
    private val viewLogRepository: ConversationViewLogRepository,
    private val queryRepository: ResponseQualityQueryRepository,
    private val responseThreadService: ResponseThreadService,
) {
    @Transactional
    fun recordView(
        adminEmail: String,
        conversationId: Long,
        memberId: Long,
    ) {
        viewLogRepository.save(ConversationViewLog(adminEmail, conversationId, memberId))
    }

    /** 응답마다 가장 최근 AI 판정. */
    @Transactional(readOnly = true)
    fun latestAiVerdicts(messageIds: Collection<Long>): Map<Long, ResponseAiVerdict> =
        if (messageIds.isEmpty()) {
            emptyMap()
        } else {
            aiVerdictRepository
                .findByMessageIdIn(messageIds)
                .groupBy { it.messageId }
                .mapValues { (_, verdicts) -> verdicts.maxBy { it.id } }
        }

    @Transactional(readOnly = true)
    fun humanVerdicts(messageIds: Collection<Long>): Map<Long, List<ResponseHumanVerdict>> =
        if (messageIds.isEmpty()) emptyMap() else humanVerdictRepository.findByMessageIdIn(messageIds).groupBy { it.messageId }

    @Transactional(readOnly = true)
    fun aiVerdictsCreatedBetween(
        from: Instant,
        to: Instant,
    ): List<ResponseAiVerdict> = aiVerdictRepository.findByMessageCreatedAtGreaterThanEqualAndMessageCreatedAtLessThan(from, to)

    @Transactional(readOnly = true)
    fun countMine(adminEmail: String): Long = humanVerdictRepository.countByAdminEmail(adminEmail)

    @Transactional
    fun saveHumanVerdict(
        adminEmail: String,
        messageId: Long,
        verdict: ResponseVerdict,
        awkwardTypes: Set<AwkwardType>,
        memo: String?,
    ): ResponseHumanVerdict {
        if (verdict == ResponseVerdict.AWKWARD && awkwardTypes.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "어색함이면 유형을 하나 이상 골라야 합니다.")
        }
        val note = memo?.trim()?.ifEmpty { null }
        humanVerdictRepository.findByMessageIdAndAdminEmail(messageId, adminEmail)?.let {
            it.change(verdict, awkwardTypes, note)
            return it
        }
        val response =
            responseThreadService.responses(listOf(messageId)).firstOrNull() ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        return humanVerdictRepository.save(
            ResponseHumanVerdict(
                messageId = messageId,
                conversationId = response.conversation.id,
                memberId = response.conversation.memberId,
                emotionType = checkNotNull(response.ref.message.emotionType),
                responseKind = response.ref.kind,
                adminEmail = adminEmail,
                verdict = verdict,
                awkwardTypes = awkwardTypes,
                memo = note,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun findResponseIds(
        filter: ResponseFilter,
        pageable: Pageable,
    ): Page<Long> {
        val total = queryRepository.countResponses(filter)
        val ids = if (total == 0L) emptyList() else queryRepository.findResponseIds(filter, pageable.offset, pageable.pageSize)
        return PageImpl(ids, pageable, total)
    }

    /** 내 판정이 가장 적은 (캐릭터, 응답 종류) 묶음에서 하나를 고른다. 판정 표본이 한쪽으로 쏠리지 않게 한다. */
    @Transactional(readOnly = true)
    fun nextLabelingTarget(
        adminEmail: String,
        exclude: Collection<Long>,
    ): Long? {
        val candidates =
            queryRepository.findLabelingCandidates(
                adminEmail,
                Instant.now().minus(LABELING_LOOKBACK),
                exclude,
                CANDIDATE_LIMIT,
            )
        if (candidates.isEmpty()) {
            return null
        }
        val done = humanVerdictRepository.findByAdminEmail(adminEmail).groupingBy { it.emotionType to it.responseKind }.eachCount()
        val byBucket = candidates.groupBy { it.emotionType to it.kind }
        val bucket = byBucket.keys.minBy { done[it] ?: 0 }
        return byBucket.getValue(bucket).random().messageId
    }

    companion object {
        private val LABELING_LOOKBACK = Duration.ofDays(30)
        private const val CANDIDATE_LIMIT = 300
    }
}
