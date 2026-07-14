package com.nexters.gamss.member.controller.dto

import com.nexters.gamss.member.domain.Nickname
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 필수입니다.")
    @field:Size(max = Nickname.MAX_LENGTH, message = "닉네임은 ${Nickname.MAX_LENGTH}자 이하여야 합니다.")
    val nickname: String,
)
