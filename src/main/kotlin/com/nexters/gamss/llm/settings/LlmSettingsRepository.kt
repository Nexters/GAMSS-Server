package com.nexters.gamss.llm.settings
import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.data.jpa.repository.JpaRepository

interface LlmSettingsRepository : JpaRepository<LlmSettings, Long> {
    fun findByPromptType(promptType: PromptType): LlmSettings?
}
