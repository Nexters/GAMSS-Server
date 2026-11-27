package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.util.UUID

data class AdjustPaidTokenRequest(
    @field:NotNull(message = "idempotencyKey는 필수입니다.")
    @field:Schema(description = "이중 클릭 방지용 멱등 키. 화면을 열 때 만든 UUID 를 재시도에도 그대로 보낸다.")
    val idempotencyKey: UUID?,
    @field:NotNull(message = "amount는 필수입니다.")
    @field:Positive(message = "amount는 양수여야 합니다.")
    // 오타로 자릿수가 밀린 지급을 막는 상한. 가장 큰 상품(100만)의 100배다.
    @field:Max(value = 100_000_000, message = "amount는 100000000 이하여야 합니다.")
    @field:Schema(description = "지급하거나 회수할 토큰 양", example = "300000")
    val amount: Long?,
    @field:NotBlank(message = "reason은 필수입니다.")
    @field:Size(max = 500, message = "reason은 500자 이하여야 합니다.")
    @field:Schema(description = "처리 사유", example = "결제 후 미지급 보상")
    val reason: String?,
)
