package com.nexters.gamss.notification.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UnregisterDeviceTokenRequest(
    @field:NotBlank(message = "token은 필수입니다.")
    @field:Schema(description = "해제할 FCM 등록 토큰", example = "fMEk...:APA91bH...")
    val token: String,
)
