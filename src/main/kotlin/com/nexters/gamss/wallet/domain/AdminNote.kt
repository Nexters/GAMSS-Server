package com.nexters.gamss.wallet.domain

/** 관리자가 직접 바꾼 변동의 처리자와 사유. 원장에 그대로 남는다. */
class AdminNote(
    val adminEmail: String,
    val reason: String,
) {
    init {
        require(adminEmail.isNotBlank() && adminEmail.length <= MAX_EMAIL_LENGTH) { "처리자 이메일 형식이 아닙니다." }
        require(reason.isNotBlank() && reason.length <= MAX_REASON_LENGTH) { "사유는 1~${MAX_REASON_LENGTH}자여야 합니다." }
    }

    companion object {
        const val MAX_EMAIL_LENGTH = 255
        const val MAX_REASON_LENGTH = 500
    }
}
