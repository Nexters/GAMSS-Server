package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.EmotionCount
import io.swagger.v3.oas.annotations.media.Schema

data class EmotionCountResponse(
    @field:Schema(description = "감정 코드", example = "ANGER")
    val emotion: String,
    @field:Schema(description = "감정 한글 라벨", example = "분노")
    val label: String,
    @field:Schema(description = "해당 감정 카드 수", example = "12")
    val count: Long,
) {
    companion object {
        fun from(count: EmotionCount): EmotionCountResponse =
            EmotionCountResponse(
                emotion = count.emotion.name,
                label = count.emotion.label,
                count = count.count,
            )
    }
}
