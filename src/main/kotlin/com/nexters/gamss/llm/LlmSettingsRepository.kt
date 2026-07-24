package com.nexters.gamss.llm

import org.springframework.data.jpa.repository.JpaRepository

interface LlmSettingsRepository : JpaRepository<LlmSettings, Long>
