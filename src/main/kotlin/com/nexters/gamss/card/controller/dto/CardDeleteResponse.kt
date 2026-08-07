package com.nexters.gamss.card.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 카드 일괄 삭제 결과. 클라이언트가 "N장을 삭제했습니다"를 보여줄 수 있게 건수를 돌려준다.
 */
data class CardDeleteResponse(
    @field:Schema(description = "이번 요청으로 삭제된 카드 수. 대상이 없으면 0", example = "3")
    val deletedCount: Int,
)
