package com.nexters.gamss.payment.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 결제 대사 배치(Google 환불, App Store 알림 기록)의 마지막 성공 시각. 실패하면 갱신하지 않아 다음 실행이 빠진 구간부터 다시 본다.
 *
 * 시각은 UTC 의 [LocalDateTime] 으로 쓰고 읽는다. `Timestamp` 를 그대로 넘기면 드라이버가 JVM 기본 시간대로 바꿔 KST 인
 * 로컬에서는 같은 순간이 9시간 어긋난 값으로 들어간다.
 */
@Repository
class PaymentJobCheckpointRepository(
    private val jdbc: JdbcTemplate,
) {
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
            LocalDateTime.ofInstant(succeededAt, ZoneOffset.UTC),
        )
    }
}
