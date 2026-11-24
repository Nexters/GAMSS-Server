package com.nexters.gamss.quality.repository

import com.nexters.gamss.quality.domain.QualityJudgeSetting
import org.springframework.data.jpa.repository.JpaRepository

interface QualityJudgeSettingRepository : JpaRepository<QualityJudgeSetting, Long>
