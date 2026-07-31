package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그아웃의 트랜잭션 작업 단위. 저장된 리프레시 토큰을 폐기해 그 토큰으로는 더 이상
 * 재발급받지 못하게 한다([LoginService]와 짝을 이루는 반대 방향 작업).
 *
 * accessToken 은 무상태라 여기서 무효화할 수 없다 — 남은 유효기간 동안은 계속 통과한다.
 */
@Service
class LogoutService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    /** 저장된 토큰이 없어도(이미 로그아웃·탈퇴 등) 조용히 성공한다 — 재호출이 안전해야 한다. */
    @Transactional
    fun logout(memberId: Long) {
        refreshTokenRepository.deleteByMemberId(memberId)
    }
}
