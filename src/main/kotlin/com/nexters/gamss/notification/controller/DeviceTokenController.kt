package com.nexters.gamss.notification.controller

import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AuthPrincipal
import com.nexters.gamss.notification.controller.dto.RegisterDeviceTokenRequest
import com.nexters.gamss.notification.controller.dto.UnregisterDeviceTokenRequest
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.service.DeviceTokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "디바이스 토큰", description = "푸시 알림을 받을 기기 등록·해제 API (모두 로그인 필요)")
@RestController
@RequestMapping("/api/members/me/device-tokens")
class DeviceTokenController(
    private val deviceTokenService: DeviceTokenService,
) {
    @Operation(
        summary = "디바이스 토큰 등록",
        description =
            "푸시 알림을 받을 기기를 등록합니다. **등록된 토큰이 있다는 것이 곧 알림 수신 동의**이고, " +
                "서버에는 별도의 수신 동의 설정이 없습니다.\n\n" +
                "- 앱 실행마다 호출해도 안전합니다(같은 토큰이면 갱신만 됩니다).\n" +
                "- 같은 기기에서 다른 계정으로 로그인하면 토큰 소유자가 새 계정으로 옮겨집니다.\n" +
                "- 기기를 여러 대 쓰면 그만큼 등록되고, 알림은 모든 기기로 갑니다.\n\n" +
                "**앱이 지켜야 할 것** — 이걸 지키지 않으면 알림을 끈 사용자에게 계속 발송을 시도합니다.\n\n" +
                "- 포그라운드 진입 시 OS 알림 권한을 확인해, 거부·해제 상태면 해제 API를 호출합니다.\n" +
                "- 로그아웃 시 해제 API를 호출합니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | token 누락 또는 공백 |\n" +
                "| INVALID_DEVICE_TOKEN | 400 | 512자를 넘는 토큰 |",
    )
    @PostMapping
    fun register(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
    ): ApiResponse<Unit> {
        deviceTokenService.register(principal.memberId, FcmToken(request.token))
        return ApiResponse.success()
    }

    @Operation(
        summary = "디바이스 토큰 해제",
        description =
            "이 기기로 알림을 보내지 않도록 등록을 해제합니다. 로그아웃하거나 OS 알림 권한이 " +
                "꺼졌을 때 호출합니다.\n\n" +
                "- 이미 해제된 토큰이어도 성공(200)입니다.\n" +
                "- 본인이 등록한 토큰만 해제됩니다(남의 토큰은 조용히 무시).\n" +
                "- 토큰이 접근 로그에 남지 않도록 쿼리 파라미터가 아니라 요청 본문으로 받습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| UNAUTHORIZED | 401 | 인증 필요(토큰 없음·무효) |\n" +
                "| EXPIRED_TOKEN | 401 | accessToken 만료 — 재발급 후 재시도 |\n" +
                "| INVALID_INPUT | 400 | token 누락 또는 공백 |\n" +
                "| INVALID_DEVICE_TOKEN | 400 | 512자를 넘는 토큰 |",
    )
    @DeleteMapping
    fun unregister(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: UnregisterDeviceTokenRequest,
    ): ApiResponse<Unit> {
        deviceTokenService.unregister(principal.memberId, FcmToken(request.token))
        return ApiResponse.success()
    }
}
