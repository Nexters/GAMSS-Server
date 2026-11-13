package com.nexters.gamss.quality.service

import com.nexters.gamss.quality.domain.QualityJudgeSetting
import com.nexters.gamss.quality.repository.QualityJudgeSettingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QualityJudgeSettingService(
    private val repository: QualityJudgeSettingRepository,
) {
    @Transactional(readOnly = true)
    fun current(): QualityJudgeSetting = requireRow()

    @Transactional
    fun update(
        autoEnabled: Boolean,
        voteCount: Int,
    ): QualityJudgeSetting = requireRow().also { it.update(autoEnabled, voteCount) }

    private fun requireRow(): QualityJudgeSetting =
        repository.findById(QualityJudgeSetting.SINGLETON_ID).orElseThrow {
            IllegalStateException("quality_judge_setting 행이 없습니다. V46 마이그레이션을 확인하세요.")
        }
}
