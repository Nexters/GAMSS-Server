package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.AppleRefundState
import com.nexters.gamss.payment.domain.StoreTransactionKey
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * App Store 서버 알림(#245)의 처리 기록과 거래별 환불 상태. [GoogleRefundRecordRepository] 와 같은 이유로 엔티티 없이
 * SQL 로 다루고, 시각은 UTC 의 [LocalDateTime] 으로 쓰고 읽는다.
 */
@Repository
class AppleNotificationRecordRepository(
    private val jdbc: JdbcTemplate,
) {
    fun isNotificationHandled(notificationUuid: String): Boolean =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM apple_notification WHERE notification_uuid = ?",
            Long::class.java,
            notificationUuid,
        )!! > 0

    fun markNotificationHandled(
        notificationUuid: String,
        type: String,
        subtype: String?,
        now: Instant,
    ) {
        jdbc.update(
            "INSERT INTO apple_notification (notification_uuid, notification_type, subtype, created_at) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE notification_uuid = notification_uuid",
            notificationUuid,
            type,
            subtype,
            utc(now),
        )
    }

    /**
     * [signedAt] 이 저장된 상태보다 최근일 때만 덮어쓴다. 비교와 쓰기가 한 문장이라 두 알림이 겹쳐 와도 더 최근 상태가 남는다.
     *
     * MySQL 은 `ON DUPLICATE KEY UPDATE` 의 대입을 왼쪽부터 적용하고 뒤 대입은 앞에서 바뀐 값을 본다. 그래서 비교 기준인
     * `state_signed_at` 을 맨 마지막에 바꾼다.
     */
    fun saveRefundStateIfNewer(
        transactionId: StoreTransactionKey,
        state: AppleRefundState,
        signedAt: Instant,
        sourceId: String,
        now: Instant,
    ) {
        val newer = "VALUES(state_signed_at) > state_signed_at"
        jdbc.update(
            "INSERT INTO apple_refund_state " +
                "(transaction_id, refunded, revocation_percentage, source_id, state_signed_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "refunded = IF($newer, VALUES(refunded), refunded), " +
                "revocation_percentage = IF($newer, VALUES(revocation_percentage), revocation_percentage), " +
                "source_id = IF($newer, VALUES(source_id), source_id), " +
                "updated_at = IF($newer, VALUES(updated_at), updated_at), " +
                "state_signed_at = GREATEST(state_signed_at, VALUES(state_signed_at))",
            transactionId.value,
            state.refunded,
            state.revocationPercentage,
            sourceId,
            utc(signedAt),
            utc(now),
            utc(now),
        )
    }

    /**
     * 잠금 읽기라 트랜잭션 안에서도 스냅샷이 아니라 커밋된 최신 상태를 본다. 공유 잠금이 상태 저장을 이 트랜잭션이 끝날
     * 때까지 기다리게 해, 읽은 상태로 회수하는 동안 더 최신 상태가 끼어들지 못한다.
     */
    fun findRefundStateForShare(transactionId: StoreTransactionKey): RecordedAppleRefund? =
        jdbc
            .query(
                "SELECT refunded, revocation_percentage, source_id FROM apple_refund_state WHERE transaction_id = ? FOR SHARE",
                { rs, _ ->
                    RecordedAppleRefund(
                        state = AppleRefundState(rs.getBoolean(1), rs.getObject(2, Integer::class.java)?.toInt()),
                        sourceId = rs.getString(3),
                    )
                },
                transactionId.value,
            ).firstOrNull()

    private fun utc(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
}

/** 저장된 환불 상태와 그 상태를 만든 알림(원장 환불 출처). */
data class RecordedAppleRefund(
    val state: AppleRefundState,
    val sourceId: String,
)
