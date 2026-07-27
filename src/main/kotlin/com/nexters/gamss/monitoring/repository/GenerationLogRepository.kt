package com.nexters.gamss.monitoring.repository

import com.nexters.gamss.monitoring.domain.GenerationLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GenerationLogRepository : JpaRepository<GenerationLog, Long> {
    /** [from] 이후의 생성 로그를 시간순으로 조회한다. 대시보드 품질 지표 집계에 쓴다. */
    @Query("select g from GenerationLog g where g.createdAt >= :from order by g.createdAt asc")
    fun findAllSince(
        @Param("from") from: Instant,
    ): List<GenerationLog>
}
