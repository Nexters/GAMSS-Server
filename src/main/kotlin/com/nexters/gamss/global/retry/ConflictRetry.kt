package com.nexters.gamss.global.retry

import org.springframework.stereotype.Component

/**
 * 동시 경합([RecoverableConflictException])을 트랜잭션 경계 바깥에서 재시도하는 정책 객체.
 *
 * 트랜잭션 안에서는 회복할 수 없다 — 유니크 제약 위반으로 트랜잭션이 rollback-only가 되기 때문이다.
 * 그래서 트랜잭션이 끝난(롤백된) 뒤 이 객체가 작업 전체를 새로 실행한다. 재시도 시점에는 앞선
 * 요청이 커밋을 마친 상태라, 다음 시도가 그 결과를 조회해 성공한다.
 *
 * 재시도 정책만 담당한다(SRP). 무엇을 재시도할지는 호출부가 람다로 넘긴다.
 */
@Component
class ConflictRetry {
    fun <T> execute(operation: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return operation()
            } catch (e: RecoverableConflictException) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e
                }
                attempt++
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
