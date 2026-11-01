package com.nexters.gamss.wallet.service

import com.nexters.gamss.global.retry.RecoverableConflictException
import com.nexters.gamss.wallet.domain.LedgerRefId

/** 같은 멱등 키의 동시 반영이나 지갑 최초 생성의 데드락. 다시 실행하면 앞선 결과를 재요청으로 돌려준다. */
class TokenLedgerConflictException(
    refId: LedgerRefId?,
    cause: Throwable,
) : RecoverableConflictException("유료 토큰 원장 반영 경합: refId=$refId", cause)
