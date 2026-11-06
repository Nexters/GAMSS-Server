package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.member.service.MemberSocialIdentityService
import com.nexters.gamss.payment.domain.PaymentAccountToken
import com.nexters.gamss.payment.repository.PaymentAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 구매 계정 토큰을 발급하고, 스토어가 돌려준 토큰으로 주인(쿼터 주체)을 찾는다.
 *
 * 토큰을 회원이 아니라 주체에 묶어 탈퇴 후 재가입해도 같은 값이 나오게 한다. 탈퇴 전에 산 구매의
 * 환불 알림이 재가입 뒤에 와도 같은 지갑을 찾아갈 수 있어야 해서다.
 */
@Service
class PaymentAccountService(
    private val repository: PaymentAccountRepository,
    private val identityService: MemberSocialIdentityService,
    private val memberService: MemberService,
) {
    /**
     * 이 회원 주체의 계정 토큰. 없으면 무작위 UUID 로 만든다. 같은 회원에게는 항상 같은 값이다.
     */
    @Transactional
    fun accountTokenOf(memberId: Long): PaymentAccountToken {
        val subjectKey = requesterSubjectKey(memberId)
        repository.createIfAbsent(subjectKey, PaymentAccountToken.of(UUID.randomUUID()).value)
        val stored = repository.findAccountTokenForShare(subjectKey) ?: error("계정 토큰을 만들지 못했습니다: subjectKey=$subjectKey")
        return requireNotNull(PaymentAccountToken.parseOrNull(stored)) { "저장된 계정 토큰 형식이 아닙니다." }
    }

    /** 스토어가 돌려준 계정 토큰의 주인. 형식이 틀리거나 발급한 적 없는 값이면 null 이다. */
    fun ownerOf(accountToken: String?): String? {
        val token = PaymentAccountToken.parseOrNull(accountToken) ?: return null
        return repository.findByAccountToken(token.value)?.subjectKey
    }

    /**
     * 요청한 회원의 쿼터 주체. 매핑이 빠지는 경우는 둘이다.
     *
     * 탈퇴로 매핑이 지워진 뒤 아직 유효한 accessToken 으로 요청하면 [ErrorCode.WITHDRAWN_MEMBER] 로
     * 거부한다(로그인·재발급과 같은 코드다). 활성 회원인데 매핑이 없으면 기동 백필이 그 회원을 채우지
     * 못한 서버 쪽 문제라, 처리되지 않은 예외로 올려 전역 핸들러가 스택과 함께 남기게 한다.
     */
    fun requesterSubjectKey(memberId: Long): String {
        identityService.subjectKeyOf(memberId)?.let { return it }
        if (memberService.getById(memberId).isWithdrawn()) {
            throw BusinessException(ErrorCode.WITHDRAWN_MEMBER)
        }
        error("쿼터 주체 매핑이 없는 활성 회원이라 결제 계정을 정할 수 없습니다: memberId=$memberId")
    }
}
