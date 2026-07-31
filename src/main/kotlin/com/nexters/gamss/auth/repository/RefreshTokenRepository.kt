package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByMemberId(memberId: Long): RefreshToken?

    /** 탈퇴 시 폐기해, 잔여 유효기간 동안 재발급이 시도되지 않게 한다. */
    fun deleteByMemberId(memberId: Long)
}
