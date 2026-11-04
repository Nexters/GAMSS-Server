package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.PaymentAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface PaymentAccountRepository : JpaRepository<PaymentAccount, String> {
    fun findByAccountToken(accountToken: String): PaymentAccount?

    /**
     * 없으면 [accountToken] 으로 만들고, 이미 있으면 그대로 둔다(첫 토큰이 계속 유지된다).
     *
     * 앱이 구매 화면에 들어올 때마다 부르므로 같은 주체의 요청이 겹칠 수 있다. 조회 후 저장하면 둘 다
     * "없음"을 보고 유니크 위반에 걸리므로 한 문장으로 처리한다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        value =
            "insert into payment_account (subject_key, account_token, created_at) " +
                "values (:subjectKey, :accountToken, now(6)) " +
                "on duplicate key update subject_key = subject_key",
        nativeQuery = true,
    )
    fun createIfAbsent(
        @Param("subjectKey") subjectKey: String,
        @Param("accountToken") accountToken: String,
    )

    /**
     * [createIfAbsent] 직후에 읽는다. 잠금 읽기로 두는 이유는 트랜잭션 스냅샷 때문이다 - 같은 트랜잭션이
     * 앞에서 다른 테이블을 이미 읽었다면 일반 SELECT 는 그때의 스냅샷을 봐서, 동시 요청이 방금 커밋한
     * 행을 upsert 는 봤는데 조회는 못 보는 일이 생긴다. 잠금 읽기는 항상 최신 커밋을 본다.
     */
    @Query(value = "select account_token from payment_account where subject_key = :subjectKey for share", nativeQuery = true)
    fun findAccountTokenForShare(
        @Param("subjectKey") subjectKey: String,
    ): String?
}
