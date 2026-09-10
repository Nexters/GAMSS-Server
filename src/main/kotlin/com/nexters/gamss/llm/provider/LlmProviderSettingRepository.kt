package com.nexters.gamss.llm.provider

import org.springframework.data.jpa.repository.JpaRepository

interface LlmProviderSettingRepository : JpaRepository<LlmProviderSetting, Long>
