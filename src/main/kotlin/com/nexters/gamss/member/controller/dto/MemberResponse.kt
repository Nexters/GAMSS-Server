package com.nexters.gamss.member.controller.dto

import com.nexters.gamss.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class MemberResponse(
    @field:Schema(description = "회원 ID", example = "1")
    val id: Long,
    @field:Schema(description = "이메일 (소셜 제공자가 제공하지 않으면 null)", example = "user@example.com")
    val email: String?,
    @field:Schema(description = "이름 (소셜 제공자가 제공하지 않으면 null)", example = "홍길동")
    val name: String?,
    @field:Schema(description = "닉네임 (탈퇴 등으로 비워진 경우가 아니면 항상 값이 있다)", example = "바다")
    val nickname: String?,
    @field:Schema(description = "회원 상태", example = "ACTIVE", allowableValues = ["ACTIVE", "WITHDRAWN"])
    val status: String,
    @field:Schema(description = "가입 일시")
    val createdAt: Instant,
) {
    companion object {
        fun from(member: Member): MemberResponse =
            MemberResponse(
                id = member.id,
                email = member.email,
                name = member.name,
                nickname = member.nickname?.value,
                status = member.status.name,
                createdAt = member.createdAt,
            )
    }
}
