package com.nexters.gamss.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 회원 닉네임 값 객체. 공백 불가·최대 길이 같은 불변식을 스스로 보장한다.
 */
@Embeddable
data class Nickname(
    @Column(name = "nickname", length = MAX_LENGTH)
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "닉네임은 공백일 수 없습니다." }
        require(value.length <= MAX_LENGTH) { "닉네임은 ${MAX_LENGTH}자 이하여야 합니다." }
    }

    companion object {
        const val MAX_LENGTH = 20
    }
}
