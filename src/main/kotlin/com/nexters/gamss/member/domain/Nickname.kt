package com.nexters.gamss.member.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.text.BreakIterator

/**
 * 회원 닉네임 값 객체. 앞뒤 공백을 정리하고 길이·금칙어 불변식을 스스로 보장한다.
 */
@Embeddable
class Nickname(
    value: String,
) {
    @Column(name = "nickname", length = COLUMN_LENGTH)
    val value: String = value.trim()

    init {
        validate(this.value)
    }

    private fun validate(value: String) {
        validateLength(value)
        validateBannedWord(value)
    }

    private fun validateLength(value: String) {
        // 컬럼(varchar, 코드 포인트 기준)을 넘는 병적인 입력(초장문 ZWJ 조합 등) 방어.
        // UTF-16 유닛 수 ≥ 코드 포인트 수이므로 이 검사로 컬럼 초과가 원천 차단된다.
        if (graphemeCount(value) in MIN_LENGTH..MAX_LENGTH && value.length <= COLUMN_LENGTH) {
            return
        }
        throw BusinessException(
            ErrorCode.INVALID_NICKNAME,
            "닉네임은 ${MIN_LENGTH}자 이상 ${MAX_LENGTH}자 이하여야 합니다.",
        )
    }

    /**
     * 사용자가 보는 글자 수(그래핌 클러스터). [String.length]는 UTF-16 유닛 수라 이모지가
     * 2자로 계산되고, 코드 포인트로 세도 피부톤·ZWJ 조합 이모지는 여러 자로 계산된다.
     */
    private fun graphemeCount(value: String): Int {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(value)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) {
            count++
        }
        return count
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

        /**
         * DB 컬럼 크기(코드 포인트 기준). 길이 검증은 그래핌 기준 [MAX_LENGTH]지만 varchar는
         * 코드 포인트를 세므로, 그래핌당 여유(약 10 코드 포인트)를 둔 크기로 컬럼을 잡는다(V25).
         */
        const val COLUMN_LENGTH = 200

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
