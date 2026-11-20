package com.nexters.gamss.game.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class RecordGameRequest(
    @field:NotNull(message = "score는 필수입니다.")
    @field:Schema(description = "게임 점수. 클리커는 제한 시간 안의 터치 횟수", example = "87")
    val score: Int?,
)
