package com.nexters.gamss.payment.service

import com.nexters.gamss.global.retry.RecoverableConflictException
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey

/** 같은 거래의 동시 지급. 다시 실행하면 앞선 지급을 찾아 재요청으로 돌려준다. */
class PaymentConflictException(
    platform: PaymentPlatform,
    storeTransactionKey: StoreTransactionKey,
    cause: Throwable,
) : RecoverableConflictException("결제 기록 경합: platform=$platform, key=$storeTransactionKey", cause)
