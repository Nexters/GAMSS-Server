package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PromptRevisionRepository : JpaRepository<PromptRevision, Long> {
    fun findAllByPromptTypeOrderByVersionDesc(
        promptType: PromptType,
        pageable: Pageable,
    ): Page<PromptRevision>

    /** 타입의 현재 최대 버전. 리비전이 없으면 null — 다음 버전은 (max ?: 0) + 1. */
    @Query("select max(r.version) from PromptRevision r where r.promptType = :promptType")
    fun findMaxVersion(
        @Param("promptType") promptType: PromptType,
    ): Int?
}
