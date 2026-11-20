package com.nexters.gamss.quality.service

import com.nexters.gamss.global.retry.RecoverableConflictException

/** 같은 관리자의 첫 판정이 동시에 들어와 유니크 제약에 걸렸다. 다시 실행하면 앞선 판정을 수정한다. */
class HumanVerdictConflictException(
    messageId: Long,
    cause: Throwable,
) : RecoverableConflictException("사람 판정 저장 경합: messageId=$messageId", cause)
