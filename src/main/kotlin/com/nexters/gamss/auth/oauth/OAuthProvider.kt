package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode

/**
 * 지원하는 소셜 제공자. 변경 가능한 목록이므로 인프라(oauth) 계층에 둔다.
 * 새 제공자 추가 = 상수 + OAuthClient 구현체 추가. (domain 은 provider 를 String 으로만 저장하므로 바뀌지 않는다)
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
