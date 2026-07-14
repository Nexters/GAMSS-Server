package com.nexters.gamss.member.controller.dto

import com.nexters.gamss.member.domain.Member
import java.time.Instant

data class MemberResponse(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val status: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(member: Member): MemberResponse =
            MemberResponse(
                id = member.id,
                email = member.email,
                nickname = member.nickname?.value,
                status = member.status.name,
                createdAt = member.createdAt,
            )
    }
}
