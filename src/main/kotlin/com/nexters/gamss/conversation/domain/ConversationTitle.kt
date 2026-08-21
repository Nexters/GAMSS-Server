package com.nexters.gamss.conversation.domain

import com.nexters.gamss.global.crypto.EncryptedStringConverter
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable

/**
 * 대화방 제목 값 객체. 앞뒤 공백을 정리하고 비어 있지 않음·최대 길이 불변식을 스스로 보장한다.
 * (카드 요약과는 무관한 독립 개념이다.)
 */
@Embeddable
class ConversationTitle(
    value: String,
) {
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "title", length = ENCRYPTED_LENGTH)
    val value: String = value.trim()

    init {
        validate(this.value)
    }

    private fun validate(value: String) {
        if (value.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_CONVERSATION_TITLE, "채팅방 제목은 비어 있을 수 없습니다.")
        }
        if (value.length > MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_CONVERSATION_TITLE, "채팅방 제목은 ${MAX_LENGTH}자 이하여야 합니다.")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ConversationTitle) {
            return false
        }
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        /** 평문 상한. 사용자에게 보이는 제약이라 검증 메시지도 이 값을 쓴다. */
        const val MAX_LENGTH = 100

        /**
         * 암호문을 담기 위한 컬럼 폭. 한글 [MAX_LENGTH]자를 암호화해 Base64 로 감싸면 약 450자가 된다.
         * 여유를 둔 값이라 평문 상한과 혼동하지 말 것.
         */
        const val ENCRYPTED_LENGTH = 600
    }
}
