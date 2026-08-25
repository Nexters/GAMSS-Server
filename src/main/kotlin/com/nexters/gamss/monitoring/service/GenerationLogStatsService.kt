package com.nexters.gamss.monitoring.service

import com.nexters.gamss.monitoring.domain.GenerationLog
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 쌓인 생성 로그를 **집계해서 돌려준다.** 백오피스 대시보드와 일일 토큰 상한 판정이 쓴다.
 *
 * 기록하는 쪽은 [GenerationLogRecorder] 다. 이 모듈에는 컨트롤러가 없어서 "기록한다"와 "집계한다"
 * 둘로 갈리고, 그 둘은 바뀌는 이유가 다르다.
 *
 * 다른 모듈이 [GenerationLogRepository] 를 직접 잡지 않게 하는 역할도 겸한다.
 */
@Service
@Transactional(readOnly = true)
class GenerationLogStatsService(
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
