package com.nexters.gamss.llm.settings
import com.nexters.gamss.llm.prompt.PromptType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LlmSettingsRepository : JpaRepository<LlmSettings, Long> {
    fun findByPromptType(promptType: PromptType): LlmSettings?

    /**
     * 타입의 설정 행을 잠그고 읽는다 — 프롬프트 저장·복원의 리비전 채번을 타입 단위로 직렬화하는
     * 잠금 지점([PromptRevisionService]). 리비전 행이 아니라 이 행을 잠그는 이유는, 채번 도중에도
     * 절대 바뀌지 않는 안정된 잠금 대상이 필요하기 때문이다(최신 리비전 행은 채번마다 새로 생겨
     * 대기 후 낡은 행을 잡는 스테일 맥스 문제가 있다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LlmSettings s where s.promptType = :promptType")
    fun findByPromptTypeForUpdate(
        @Param("promptType") promptType: PromptType,
    ): LlmSettings?
}
