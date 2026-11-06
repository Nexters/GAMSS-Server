package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentPlatform
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByPlatformAndStoreTransactionKey(
        platform: PaymentPlatform,
        storeTransactionKey: String,
    ): Payment?

    /**
     * 회수에서 쓴다. 같은 환불이 웹훅과 대사 배치에서 겹쳐 와도 두 번째는 앞선 커밋을 기다렸다가
     * 이미 맞춰진 [Payment.refundedTokens] 를 보고 차이 0 으로 끝난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.platform = :platform and p.storeTransactionKey = :storeTransactionKey")
    fun findByPlatformAndStoreTransactionKeyForUpdate(
        @Param("platform") platform: PaymentPlatform,
        @Param("storeTransactionKey") storeTransactionKey: String,
    ): Payment?
}
