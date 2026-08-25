package com.nexters.gamss.global.response

import com.nexters.gamss.global.exception.ErrorKind
import org.springframework.http.HttpStatus

/**
 * [ErrorKind]를 HTTP 상태 코드로 옮긴다. 이 번역은 웹 계층만의 관심사라 여기에 둔다.
 *
 * `when` 이 exhaustive 라 [ErrorKind]에 값을 더하면 여기서 컴파일이 깨진다. 새 성격을 추가하면서
 * 상태 코드를 정하는 것을 잊을 수 없다는 뜻이다.
 */
fun httpStatusOf(kind: ErrorKind): HttpStatus =
    when (kind) {
        ErrorKind.INVALID_INPUT -> HttpStatus.BAD_REQUEST
        ErrorKind.UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED
        ErrorKind.PERMISSION_DENIED -> HttpStatus.FORBIDDEN
        ErrorKind.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorKind.CONFLICT -> HttpStatus.CONFLICT
        ErrorKind.QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS
        ErrorKind.UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
        ErrorKind.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
    }
