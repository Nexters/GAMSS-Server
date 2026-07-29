package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import com.nexters.gamss.tokenlimit.repository.TokenPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 일일 토큰 상한 정책([TokenPolicy])의 단일 행을 읽고 갱신한다(백오피스에서 조절). 시드(V15)로 항상
 * 한 행이 존재하지만, 방어적으로 행이 없으면 코드 기본값을 새로 만들어 반환한다.
 */
@Service
class TokenPolicyService(
    private val tokenPolicyRepository: TokenPolicyRepository,
) {
    @Transactional(readOnly = true)
    fun current(): TokenPolicy = tokenPolicyRepository.findAll().firstOrNull() ?: ensureRow()

    @Transactional
    fun update(
        dailyTokenLimit: Long,
        resetHour: Int,
    ): TokenPolicy {
        val policy = tokenPolicyRepository.findAll().firstOrNull() ?: ensureRow()
        policy.update(dailyTokenLimit, resetHour)
        return tokenPolicyRepository.save(policy)
    }

    @Transactional
    fun ensureRow(): TokenPolicy =
        tokenPolicyRepository.findAll().firstOrNull()
            ?: tokenPolicyRepository.save(TokenPolicy(DEFAULT_DAILY_TOKEN_LIMIT, DEFAULT_RESET_HOUR))

    companion object {
        const val DEFAULT_DAILY_TOKEN_LIMIT = 100_000L
        const val DEFAULT_RESET_HOUR = 5
    }
}
