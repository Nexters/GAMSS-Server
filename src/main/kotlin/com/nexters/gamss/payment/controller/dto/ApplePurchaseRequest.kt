package com.nexters.gamss.payment.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ApplePurchaseRequest(
    // 인증서 체인이 함께 실려 실제 값은 수 KB 다. 상한이 없으면 거대한 문자열을 그대로 서명 검증에 넘긴다.
    @field:NotBlank(message = "signedTransaction은 필수입니다.")
    @field:Size(max = 16384, message = "signedTransaction은 16384자 이하여야 합니다.")
    @field:Schema(description = "StoreKit 2 거래의 JWS(Transaction 의 jwsRepresentation)")
    val signedTransaction: String,
)
