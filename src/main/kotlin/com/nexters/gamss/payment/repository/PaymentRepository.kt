package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentPlatform
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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

    /**
     * consume 된 시각을 처음 한 번만 기록한다. 엔티티를 읽어 고친 뒤 저장하면 변경 감지가 행 전체를
     * 쓰면서, 그 사이 회수가 맞춘 [Payment.refundedTokens] 를 읽어 둔 옛 값으로 덮을 수 있다. 이 컬럼만
     * 조건부로 바꾸면 그런 일이 없고, 앱 요청과 스케줄러가 겹쳐도 먼저 쓴 시각이 남는다.
     *
     * @return 바뀐 행 수. 이미 기록돼 있었으면 0 이다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Payment p set p.consumedAt = :consumedAt where p.id = :id and p.consumedAt is null")
    fun markConsumed(
        @Param("id") id: Long,
        @Param("consumedAt") consumedAt: Instant,
    ): Int

    /**
     * consume 재시도 대상. 오래된 것부터 [pageable] 크기만큼 가져온다.
     *
     * [purchasedAfter] 보다 오래된 구매는 Google 이 이미 자동 환불했을 것이라 consume 할 수 없다. 이
     * 조건이 없으면 그런 행이 영원히 대상으로 남는다. [createdBefore] 는 막 지급돼 앱 요청 경로나 RTDN
     * 웹훅(#246)이 아직 consume 하고 있을 행을 건너뛰려는 것이다.
     */
    @Query(
        "select p from Payment p where p.platform = :platform and p.consumedAt is null " +
            "and p.purchasedAt > :purchasedAfter and p.createdAt < :createdBefore order by p.purchasedAt asc, p.id asc",
    )
    fun findUnconsumed(
        @Param("platform") platform: PaymentPlatform,
        @Param("purchasedAfter") purchasedAfter: Instant,
        @Param("createdBefore") createdBefore: Instant,
        pageable: Pageable,
    ): List<Payment>
}
