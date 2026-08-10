package com.nexters.gamss.global.retry

/**
 * 동시 요청 경합으로 유니크 제약에 걸렸지만, 앞선 요청이 커밋된 뒤 작업 전체를 다시 실행하면
 * 해소되는 **회복 가능한 충돌**. 영속성 예외(DataIntegrityViolationException 등)를 도메인별로
 * 이 타입의 하위 예외로 번역해, 재시도 계층([ConflictRetry])이 특정 영속성 기술이나 도메인에
 * 의존하지 않게 한다.
 */
abstract class RecoverableConflictException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
