package com.nexters.gamss.member.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 회원 닉네임 값 객체. 앞뒤 공백을 정리하고 길이·금칙어 불변식을 스스로 보장한다.
 */
@Embeddable
class Nickname(
    value: String,
) {
    @Column(name = "nickname", length = MAX_LENGTH)
    val value: String = value.trim()

    init {
        if (this.value.length !in MIN_LENGTH..MAX_LENGTH) {
            throw BusinessException(
                ErrorCode.INVALID_NICKNAME,
                "닉네임은 ${MIN_LENGTH}자 이상 ${MAX_LENGTH}자 이하여야 합니다.",
            )
        }
        if (BANNED_WORDS.any { this.value.contains(it, ignoreCase = true) }) {
            throw BusinessException(ErrorCode.INVALID_NICKNAME, "닉네임에 사용할 수 없는 표현이 포함되어 있습니다.")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Nickname) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 20

        // 금칙어 시작 목록. 필요 시 확장한다.
        private val BANNED_WORDS = setOf("시발", "씨발", "새끼", "병신", "지랄", "좆", "썅")
    }
}
