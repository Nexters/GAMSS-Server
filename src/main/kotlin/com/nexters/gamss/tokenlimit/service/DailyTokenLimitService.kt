package com.nexters.gamss.tokenlimit.service

import com.nexters.gamss.tokenlimit.domain.TokenQuotaWindow
import com.nexters.gamss.tokenlimit.repository.TokenQuotaUsageRepository
import com.nexters.gamss.wallet.service.TokenWalletReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 유저별 일일 토큰 상한을 판정한다. 상한 '적용 여부'는 코드 설정([enabled], 배포 환경 dev/prod에서 true)이,
 * 상한 '값·리셋 시각'은 백오피스가 조절하는 [TokenPolicy]가 정한다. 상한이 꺼진 환경(local)에서는
 * 항상 통과시킨다. dev도 상한을 켜는 이유는 막기 위해서가 아니라 클라이언트가 사용량 응답(dailyLimit)을
 * 실제 값으로 받아보게 하기 위해서다 — 막히지 않도록 dev의 상한 값은 백오피스에서 크게 잡아둔다.
 *
 * '하루'의 경계는 자정이 아니라 정책의 리셋 시각(KST)이다: 예) resetHour=5면 오늘 05:00 ~ 내일 05:00 이
 * 한 구간이고, 그 구간의 소비 합이 상한에 닿고 유료 토큰도 없으면 생성을 막는다(저장은 이미 끝났으므로 생성만 차단).
 *
 * 사용량은 회원이 아니라 소셜 신원에서 나온 쿼터 주체에 귀속된다([TokenQuotaRecorder]) - 예전처럼
 * `generation_log` 를 `member_id` 로 합산하면 탈퇴 후 재가입으로 한도가 리셋된다(#222).
 *
 * 일일 토큰을 다 쓰면 구매한 유료 토큰으로 이어서 쓴다(#239). 환불 회수로 잔액이 음수여도 일일 토큰이
 * 남아 있는 동안은 생성할 수 있다.
 */
@Service
class DailyTokenLimitService(
    private val tokenPolicyService: TokenPolicyService,
    private val tokenQuotaUsageRepository: TokenQuotaUsageRepository,
    private val tokenWalletReader: TokenWalletReader,
    @Value("\${gamss.token-limit.enabled:false}") private val enabled: Boolean,
) {
    /** 이 회원이 지금 생성해도 되는지(=일일 토큰이나 유료 토큰이 남음). 상한이 꺼진 환경이면 항상 true. */
    fun isWithinLimit(memberId: Long): Boolean {
        if (!enabled) {
            return true
        }
        val policy = tokenPolicyService.current()
        return canGenerate(usedTokens(memberId, policy.resetHour), policy.dailyTokenLimit) { tokenWalletReader.balanceOf(memberId) }
    }

    /**
     * 이 회원의 오늘(정책 리셋 시각 기준) 토큰 사용량과 유료 잔액. 상한이 꺼진 환경([enabled]=false)이면
     * [TokenUsage.dailyLimit]이 null(무제한)이고 [TokenUsage.exceeded]는 항상 false다.
     * [TokenUsage.exceeded]는 [isWithinLimit]과 같은 판정식([canGenerate])의 반대다.
     * [TokenUsage.paidBalance]는 상한 적용 여부와 상관없이 실제 잔액을 담는다.
     */
    fun usageFor(memberId: Long): TokenUsage {
        val policy = tokenPolicyService.current()
        val used = usedTokens(memberId, policy.resetHour)
        val paidBalance = tokenWalletReader.balanceOf(memberId)
        if (!enabled) {
            return TokenUsage(usedTokens = used, dailyLimit = null, exceeded = false, paidBalance = paidBalance)
        }
        return TokenUsage(
            usedTokens = used,
            dailyLimit = policy.dailyTokenLimit,
            exceeded = !canGenerate(used, policy.dailyTokenLimit) { paidBalance },
            paidBalance = paidBalance,
        )
    }

    /**
     * 생성 가능 판정식: 일일 토큰이 남았거나 유료 잔액이 양수다. 생성 차단과 사용량 응답이 이 식 하나를 쓴다.
     * 잔액은 일일 토큰이 바닥났을 때만 [paidBalance]로 읽는다 - 생성([isWithinLimit])마다 지갑을 조회하지 않기 위해서다.
     */
    private inline fun canGenerate(
        usedTokens: Long,
        dailyLimit: Long,
        paidBalance: () -> Long,
    ): Boolean = usedTokens < dailyLimit || paidBalance() > 0

    /** 주체 매핑이 없으면 0 이다 - 적립도 안 되고 있어 한도가 사실상 꺼진 상태다. */
    private fun usedTokens(
        memberId: Long,
        resetHour: Int,
    ): Long = tokenQuotaUsageRepository.findUsedTokens(memberId, TokenQuotaWindow.startOf(resetHour))
}
