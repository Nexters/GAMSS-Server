package com.nexters.gamss.global.exception

/**
 * 도메인 비즈니스 예외. 에러 코드로 HTTP 상태·메시지를 표현한다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    message: String? = null,
) : RuntimeException(message ?: errorCode.message)
