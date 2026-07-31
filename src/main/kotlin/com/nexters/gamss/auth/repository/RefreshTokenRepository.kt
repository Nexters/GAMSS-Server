package com.nexters.gamss.auth.repository

import com.nexters.gamss.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByMemberId(memberId: Long): RefreshToken?

    /** 저장된 토큰을 폐기한다(로그아웃 등). 행이 없어도 예외 없이 no-op 이다. */
    fun deleteByMemberId(memberId: Long)
}
