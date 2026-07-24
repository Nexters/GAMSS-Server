package com.nexters.gamss.admin.controller

import com.nexters.gamss.admin.controller.dto.AddAdminAccountRequest
import com.nexters.gamss.admin.controller.dto.AdminAccountResponse
import com.nexters.gamss.admin.service.AdminAccountEntry
import com.nexters.gamss.admin.service.AdminAccountService
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.security.AdminPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "백오피스 관리자",
    description = "백오피스 접근 허용 관리자 조회·추가·삭제 API (ROLE_ADMIN 필요). 재배포 없이 즉시 반영.",
)
@RestController
@RequestMapping("/api/admin/accounts")
class AdminAccountController(
    private val adminAccountService: AdminAccountService,
) {
    @Operation(
        summary = "관리자 목록 조회",
        description = "허용 관리자를 반환합니다. ENV 부트스트랩(삭제 불가)과 DB 관리 항목을 함께 보여줍니다.",
    )
    @GetMapping
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
    ): ApiResponse<List<AdminAccountResponse>> =
        ApiResponse.success(adminAccountService.list(principal.email).map { AdminAccountResponse.from(it) })

    @Operation(
        summary = "관리자 추가",
        description =
            "이메일로 관리자를 추가합니다. 다음 로그인부터 즉시 허용됩니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| INVALID_INPUT | 400 | email 누락 또는 형식 오류 |\n" +
                "| ADMIN_ACCOUNT_ALREADY_EXISTS | 409 | 이미 허용된(ENV·DB) 관리자 |",
    )
    @PostMapping
    fun add(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @Valid @RequestBody request: AddAdminAccountRequest,
    ): ApiResponse<AdminAccountResponse> {
        val account = adminAccountService.add(checkNotNull(request.email), principal.email)
        // 방금 추가한 계정은 남을 추가한 것이라(본인은 이미 허용돼 추가 불가) 삭제 가능하다.
        return ApiResponse.success(AdminAccountResponse.from(AdminAccountEntry.db(account, removable = true)))
    }

    @Operation(
        summary = "관리자 삭제",
        description =
            "DB로 관리되는 관리자를 삭제합니다. 자기 자신은 삭제할 수 없습니다.\n\n" +
                "**실패 응답**\n\n" +
                "| error.code | HTTP | 설명 |\n" +
                "|---|---|---|\n" +
                "| ADMIN_ACCOUNT_NOT_FOUND | 404 | 존재하지 않는 관리자 |\n" +
                "| CANNOT_REMOVE_SELF | 409 | 자기 자신은 삭제 불가 |",
    )
    @DeleteMapping("/{id}")
    fun remove(
        @Parameter(hidden = true) @AuthenticationPrincipal principal: AdminPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        adminAccountService.remove(id, principal.email)
        return ApiResponse.success()
    }
}
