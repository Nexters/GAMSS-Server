package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseVerdict
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class HumanVerdictRequest(
    @field:NotNull(message = "verdict는 필수입니다.")
    @field:Schema(description = "판정", example = "AWKWARD")
    val verdict: ResponseVerdict?,
    @field:Schema(description = "어색함 유형. verdict 가 AWKWARD 일 때 하나 이상")
    val awkwardTypes: Set<AwkwardType> = emptySet(),
    @field:Size(max = 500, message = "메모는 500자 이하여야 합니다.")
    @field:Schema(description = "메모", nullable = true)
    val memo: String? = null,
)
