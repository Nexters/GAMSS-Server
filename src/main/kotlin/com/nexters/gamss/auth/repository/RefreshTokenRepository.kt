package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByMemberId(memberId: Long): RefreshToken?

    /**
     * 저장된 토큰을 폐기한다. 행이 없어도 예외 없이 no-op 이다.
     * - 로그아웃: 그 토큰으로 더는 재발급받지 못하게 한다.
     * - 탈퇴: 잔여 유효기간(최대 14일) 동안 재발급이 시도되지 않게 한다.
     */
    fun deleteByMemberId(memberId: Long)
}
