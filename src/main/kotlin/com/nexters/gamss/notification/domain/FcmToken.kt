package com.nexters.gamss.notification.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * FCM 등록 토큰 값 객체. 앞뒤 공백을 정리하고 빈 값·컬럼 초과를 스스로 막는다.
 *
 * 토큰 형식은 FCM 이 정하고 바뀔 수 있어(길이 상한도 문서에 명시돼 있지 않다) 형식 자체는 검증하지
 * 않는다 — 여기서 막는 것은 **저장할 수 없는 값**뿐이고, 실제로 살아 있는 토큰인지는 발송 응답이
 * 알려준다. 그래서 정규식으로 조이지 않고 길이 상한만 컬럼([COLUMN_LENGTH])에 맞춘다.
 */
@Embeddable
class FcmToken(
    value: String,
) {
    @Column(name = "token", nullable = false, length = COLUMN_LENGTH)
    val value: String = value.trim()

    init {
        validate(this.value)
    }

    private fun validate(value: String) {
        if (value.isNotBlank() && value.length <= COLUMN_LENGTH) {
            return
        }
        throw BusinessException(
            ErrorCode.INVALID_DEVICE_TOKEN,
            "디바이스 토큰은 1자 이상 ${COLUMN_LENGTH}자 이하여야 합니다.",
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is FcmToken) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    /** 토큰 전체는 기기를 특정하는 값이라 로그에 남지 않게 앞부분만 남긴다. */
    override fun toString(): String = "FcmToken(${value.take(MASK_LENGTH)}...)"

    companion object {
        /** DB 컬럼 크기(V30). FCM 토큰은 보통 300자 안쪽이지만 상한이 보장돼 있지 않아 여유를 둔다. */
        const val COLUMN_LENGTH = 512

        private const val MASK_LENGTH = 8
    }
}
