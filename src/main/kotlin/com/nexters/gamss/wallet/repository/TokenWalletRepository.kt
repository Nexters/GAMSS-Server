package com.nexters.gamss.wallet.repository

import com.nexters.gamss.wallet.domain.TokenWallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface TokenWalletRepository : JpaRepository<TokenWallet, String> {
    /** 이 회원이 속한 주체의 잔액. 매핑이나 지갑이 없으면 0 이다. */
    @Query(
        value =
            "select coalesce(sum(w.balance), 0) from member_social_identity i " +
                "left join token_wallet w on w.subject_key = i.subject_key " +
                "where i.member_id = :memberId",
        nativeQuery = true,
    )
    fun findBalance(
        @Param("memberId") memberId: Long,
    ): Long

    /** 동시 최초 생성에서 유니크 위반이 나지 않게 upsert 로 만든다. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        value =
            "insert into token_wallet (subject_key, balance, updated_at) values (:subjectKey, 0, now(6)) " +
                "on duplicate key update subject_key = subject_key",
        nativeQuery = true,
    )
    fun createIfAbsent(
        @Param("subjectKey") subjectKey: String,
    )

    /** 영속성 컨텍스트에 먼저 읽힌 엔티티가 있어도 낡은 값을 쓰지 않도록 스칼라로 읽는다. */
    @Query(value = "select balance from token_wallet where subject_key = :subjectKey for update", nativeQuery = true)
    fun findBalanceForUpdate(
        @Param("subjectKey") subjectKey: String,
    ): Long?

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
        value = "update token_wallet set balance = balance + :delta, updated_at = now(6) where subject_key = :subjectKey",
        nativeQuery = true,
    )
    fun addBalance(
        @Param("subjectKey") subjectKey: String,
        @Param("delta") delta: Long,
    ): Int
}
