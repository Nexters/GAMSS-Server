package com.nexters.gamss.tokenlimit.domain

import java.io.Serializable
import java.time.Instant

/** [TokenQuotaUsage] 의 복합 키. 기본값은 JPA 가 인스턴스를 만들 수 있게 두는 것이고 의미는 없다. */
data class TokenQuotaUsageId(
    val subjectKey: String = "",
    val windowStart: Instant = Instant.EPOCH,
) : Serializable
