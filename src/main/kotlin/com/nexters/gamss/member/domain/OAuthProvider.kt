package com.nexters.gamss.member.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode

/**
 * 소셜 로그인 제공자. 새 제공자는 여기에 상수만 추가한다.
 */
enum class OAuthProvider {
    GOOGLE,
    APPLE,
    ;

    companion object {
        fun from(value: String): OAuthProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER)
    }
}
