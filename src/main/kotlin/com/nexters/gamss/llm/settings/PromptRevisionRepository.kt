package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PromptRevisionRepository : JpaRepository<PromptRevision, Long> {
    fun findAllByPromptTypeOrderByVersionDesc(
        promptType: PromptType,
        pageable: Pageable,
    ): Page<PromptRevision>

    /**
     * 타입의 최신 리비전을 잠그고 읽는다 — 채번(max+1)의 조회와 저장 사이에 다른 저장이 끼어들면
     * 같은 버전을 쓰게 되므로, 최신 행을 잠가 같은 타입의 채번을 직렬화한다. 리비전이 아직 없어
     * 잠글 행이 없는 첫 기록만 경합이 열리는데, uk(prompt_type, version) 제약이 최후 방어로 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select r from PromptRevision r " +
            "where r.promptType = :promptType " +
            "and r.version = (select max(r2.version) from PromptRevision r2 where r2.promptType = :promptType)",
    )
    fun findLatestForUpdate(
        @Param("promptType") promptType: PromptType,
    ): PromptRevision?
}
