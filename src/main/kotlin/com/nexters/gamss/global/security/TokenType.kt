package com.nexters.gamss.global.security

/**
 * 서비스 토큰의 종류. 토큰에 클레임으로 실어 access/refresh 를 구분한다.
 * 구분이 없으면 refresh 토큰으로도 인증이 통과한다.
 */
enum class TokenType {
    ACCESS,
    REFRESH,
    ;

    companion object {
        fun from(value: String?): TokenType? = entries.find { it.name == value }
    }
}
