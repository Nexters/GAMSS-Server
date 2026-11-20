package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.GoogleNotificationKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Google 환불 회수(#246)의 처리 기록 세 가지: RTDN 메시지, 대사 배치 항목, 배치의 마지막 성공 시각.
 *
 * 모두 "이미 했는가"만 묻는 작은 테이블이라 엔티티 없이 SQL 로 다룬다. 기록은 같은 키가 겹쳐 와도 예외 없이
 * 끝나야 한다(같은 메시지를 두 요청이 동시에 처리한 경우 등). 처리 자체가 멱등이라 두 번째 기록은 버려도 되므로,
 * 다른 저장소처럼 아무것도 바꾸지 않는 upsert(`ON DUPLICATE KEY UPDATE x = x`)로 쓴다.
 *
 * 시각은 UTC 의 [LocalDateTime] 으로 바꿔 쓰고 읽는다. Hibernate 는 [Instant] 를 UTC 로 저장하는데, `Timestamp` 를 그대로
 * 넘기면 드라이버가 JVM 기본 시간대로 바꿔 KST 인 로컬에서는 같은 순간이 9시간 어긋난 값으로 들어간다.
 */
@Repository
class GoogleRefundRecordRepository(
    private val jdbc: JdbcTemplate,
) {
    fun isNotificationHandled(messageId: String): Boolean =
        jdbc.queryForObject("SELECT COUNT(*) FROM google_notification WHERE message_id = ?", Long::class.java, messageId)!! > 0

    fun markNotificationHandled(
        messageId: String,
        kind: GoogleNotificationKind,
        now: Instant,
    ) {
        jdbc.update(
            "INSERT INTO google_notification (message_id, notification_kind, created_at) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE message_id = message_id",
            messageId,
            kind.name,
            utc(now),
        )
    }

    fun isVoidedPurchaseHandled(
        storeTransactionKey: String,
        sourceId: String,
    ): Boolean =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM google_voided_purchase WHERE store_transaction_key = ? AND source_id = ?",
            Long::class.java,
            storeTransactionKey,
            sourceId,
        )!! > 0

    /** [voidedSource]·[voidedReason] 는 Voided Purchases API 값 그대로다. 지불 거절·사기 같은 사유를 나중에 찾으려고 남긴다. */
    fun markVoidedPurchaseHandled(
        storeTransactionKey: String,
        sourceId: String,
        voidedSource: Int?,
        voidedReason: Int?,
        now: Instant,
    ) {
        jdbc.update(
            "INSERT INTO google_voided_purchase (store_transaction_key, source_id, voided_source, voided_reason, created_at) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE source_id = source_id",
            storeTransactionKey,
            sourceId,
            voidedSource,
            voidedReason,
            utc(now),
        )
    }

    fun lastSucceededAt(jobName: String): Instant? =
        jdbc
            .query("SELECT last_succeeded_at FROM payment_job_checkpoint WHERE job_name = ?", { rs, _ ->
                rs.getObject(1, LocalDateTime::class.java).toInstant(ZoneOffset.UTC)
            }, jobName)
            .firstOrNull()

    fun saveLastSucceededAt(
        jobName: String,
        succeededAt: Instant,
    ) {
        jdbc.update(
            "INSERT INTO payment_job_checkpoint (job_name, last_succeeded_at) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE last_succeeded_at = VALUES(last_succeeded_at)",
            jobName,
            utc(succeededAt),
        )
    }

    private fun utc(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
}
