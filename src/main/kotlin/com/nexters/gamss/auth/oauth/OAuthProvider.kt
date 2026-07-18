package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode

/**
 * 지원하는 소셜 제공자. 변경 가능한 목록이므로 인프라(oauth) 계층에 둔다.
 * (domain 은 provider 를 String 으로만 저장하므로 바뀌지 않는다)
 */
enum class OAuthProvider {
    GOOGLE,
    APPLE,
    ;

    companion object {
        /** Firebase 토큰의 sign_in_provider 값을 매핑한다. 지원하지 않는 수단은 거부한다. */
        fun fromFirebase(signInProvider: String?): OAuthProvider =
            when (signInProvider) {
                "google.com" -> GOOGLE
                "apple.com" -> APPLE
                else -> throw BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER)
            }
    }
}
