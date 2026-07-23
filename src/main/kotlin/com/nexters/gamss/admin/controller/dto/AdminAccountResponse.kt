package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.admin.service.AdminAccountEntry
import com.nexters.gamss.admin.service.AdminAccountSource
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class AdminAccountResponse(
    @field:Schema(description = "DB 관리 항목의 ID(ENV 부트스트랩은 null)", example = "1")
    val id: Long?,
    @field:Schema(description = "관리자 이메일", example = "teammate@gmail.com")
    val email: String,
    @field:Schema(description = "출처", example = "DB", allowableValues = ["ENV", "DB"])
    val source: String,
    @field:Schema(description = "UI에서 삭제 가능한지(ENV 부트스트랩은 false)", example = "true")
    val removable: Boolean,
    @field:Schema(description = "이 관리자를 추가한 관리자 이메일(ENV·초기값은 null)", example = "admin@gamss.kr")
    val addedByEmail: String?,
    @field:Schema(description = "추가 일시(ENV는 null)")
    val createdAt: Instant?,
) {
    companion object {
        fun from(entry: AdminAccountEntry): AdminAccountResponse =
            AdminAccountResponse(
                id = entry.id,
                email = entry.email,
                source = entry.source.name,
                removable = entry.source == AdminAccountSource.DB,
                addedByEmail = entry.createdByEmail,
                createdAt = entry.createdAt,
            )
    }
}
