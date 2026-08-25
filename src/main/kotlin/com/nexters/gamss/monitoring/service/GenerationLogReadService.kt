package com.nexters.gamss.monitoring.service

import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 생성 로그를 **모듈 밖에서** 읽어가는 창구(백오피스 집계, 일일 토큰 상한 판정).
 *
 * 다른 모듈이 [GenerationLogRepository] 를 직접 잡지 않게 하려고 둔다. 기록하는 쪽은
 * [GenerationLogRecorder] 가 맡는다. 읽기와 쓰기를 한 클래스에 섞지 않는다.
 */
@Service
@Transactional(readOnly = true)
class GenerationLogReadService(
    private val generationLogRepository: GenerationLogRepository,
) {
    /** [from] 이후의 생성 로그(미리보기 제외). 집계는 받는 쪽이 한다. */
    fun findAllSince(from: Instant): List<GenerationLog> = generationLogRepository.findAllSince(from)

    /** [conversationIds] 에 속한 생성 로그. conversation_id 가 없는 과거 로그는 빠진다. */
    fun findByConversationIds(conversationIds: Collection<Long>): List<GenerationLog> =
        generationLogRepository.findByConversationIdIn(conversationIds)

    /** 이 회원이 [from] 이후 소비한 토큰 합. */
    fun sumUsedTokensByMemberSince(
        memberId: Long,
        from: Instant,
    ): Long = generationLogRepository.sumUsedTokensByMemberSince(memberId, from)
}
