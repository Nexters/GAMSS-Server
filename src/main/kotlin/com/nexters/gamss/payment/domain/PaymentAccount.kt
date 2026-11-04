package com.nexters.gamss.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 쿼터 주체와 구매 계정 토큰의 바인딩. 주체마다 하나다.
 *
 * 주체에 묶이므로 탈퇴 후 재가입해도 같은 토큰이 나온다. 웹훅으로만 들어온 구매(#245, #246)는 이
 * 토큰으로 주인을 찾으므로 탈퇴 정리 대상이 아니다. 행은 upsert 로만 만든다
 * ([com.nexters.gamss.payment.repository.PaymentAccountRepository.createIfAbsent]).
 */
@Entity
@Table(name = "payment_account")
class PaymentAccount(
    @Id
    @Column(name = "subject_key", length = 64, nullable = false, updatable = false)
    val subjectKey: String,
    @Column(name = "account_token", length = PaymentAccountToken.LENGTH, nullable = false, updatable = false)
    val accountToken: String,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant,
)
