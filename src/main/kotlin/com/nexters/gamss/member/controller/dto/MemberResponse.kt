package com.nexters.gamss.member.controller.dto

import com.nexters.gamss.member.domain.Member

data class MemberResponse(
    val id: Long,
    val email: String?,
) {
    companion object {
        fun from(member: Member): MemberResponse = MemberResponse(id = member.id, email = member.email)
    }
}
