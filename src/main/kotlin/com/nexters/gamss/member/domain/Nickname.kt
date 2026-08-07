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
        validate(this.value)
    }

    private fun validate(value: String) {
        validateLength(value)
        validateBannedWord(value)
    }

    private fun validateLength(value: String) {
        if (value.length in MIN_LENGTH..MAX_LENGTH) {
            return
        }
        throw BusinessException(
            ErrorCode.INVALID_NICKNAME,
            "닉네임은 ${MIN_LENGTH}자 이상 ${MAX_LENGTH}자 이하여야 합니다.",
        )
    }

    private fun validateBannedWord(value: String) {
        if (BANNED_WORDS.none { value.contains(it, ignoreCase = true) }) {
            return
        }
        throw BusinessException(ErrorCode.INVALID_NICKNAME, "닉네임에 사용할 수 없는 표현이 포함되어 있습니다.")
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

        /**
         * 검증 실패를 예외 대신 null 로 돌려준다. 소셜 이름처럼 규칙에 맞으면 쓰고
         * 아니면 미설정으로 두는 흐름용. 검증 실패 외의 예외는 그대로 전파한다.
         */
        fun tryCreate(value: String?): Nickname? {
            if (value == null) {
                return null
            }
            return try {
                Nickname(value)
            } catch (e: BusinessException) {
                null
            }
        }
    }
}
