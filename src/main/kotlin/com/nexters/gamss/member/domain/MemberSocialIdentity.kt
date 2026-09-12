package com.nexters.gamss.member.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

/**
 * 회원과 쿼터 주체를 잇는 색인. 토큰 한도가 회원이 아니라 소셜 신원에 귀속되게 한다(#222).
 *
 * 탈퇴 시 지운다([com.nexters.gamss.member.service.MemberSocialIdentityCleaner]). 지워도 이월은
 * 깨지지 않는다 - [subjectKey] 가 소셜 신원에서 결정론적으로 나오므로 재가입하면 같은 키로 다시
 * 생기고 사용량이 쌓인 행에 붙는다.
 *
 * [subjectKey] 는 `HMAC-SHA256(encryption.index-key, "<provider>:<providerId>")` 의 hex 라
 * `provider_id` 원문을 남기지 않는다([com.nexters.gamss.member.service.SubjectKeyGenerator]).
 */
@Entity
@Table(name = "member_social_identity")
@EntityListeners(AuditingEntityListener::class)
class MemberSocialIdentity(
    /** 회원당 하나. 동시 최초 로그인의 중복 삽입이 DB 에서 걸리도록 PK 로 둔다(V38). */
    @Id
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "subject_key", length = 64, nullable = false)
    val subjectKey: String,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
