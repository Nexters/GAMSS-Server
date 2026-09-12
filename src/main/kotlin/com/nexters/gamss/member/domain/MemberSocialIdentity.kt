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
 * 회원과 **쿼터 주체**를 잇는 매핑. 토큰 한도가 회원이 아니라 소셜 신원에 귀속되게 하는 고리다(#222).
 *
 * ### 탈퇴 시 지우지 않는다
 *
 * `social_accounts` 는 `(provider, provider_id)` 유니크 제약 때문에 탈퇴 시 하드 삭제해야 한다
 * ([com.nexters.gamss.auth.service.AuthResourceCleaner]) - 남기면 같은 소셜 계정으로 재로그인해도
 * 탈퇴한 회원이 조회돼 재가입이 영영 막힌다. 그래서 지금까지는 탈퇴 전후를 이어줄 식별자가 하나도
 * 없었고, 재가입하면 토큰 사용량이 항상 0부터 시작했다.
 *
 * 이 행이 그 고리다. **[com.nexters.gamss.member.service.WithdrawnMemberCleaner] 구현을 만들면 안
 * 된다** - 만드는 순간 #222 가 그대로 되돌아온다.
 *
 * ### 그렇다고 영구히 두지도 않는다
 *
 * 막으려는 것은 **하루** 한도다. 한 구간만 지나면 연결이 없어도 어차피 한도가 리셋될 시점이라,
 * 탈퇴한 회원의 행은 한 구간 뒤에 지운다([com.nexters.gamss.tokenlimit.service.TokenQuotaRetentionScheduler]).
 * 기능 손실 없이 "탈퇴자를 과거 행적에 영구히 연결할 수 있는 상태"를 만들지 않기 위한 것이다.
 *
 * ### 소셜 식별자 원문을 담지 않는다
 *
 * [subjectKey] 는 `HMAC-SHA256(encryption.index-key, "<provider>:<providerId>")` 의 hex 다
 * ([com.nexters.gamss.member.service.SubjectKeyGenerator]). 키 없이는 역산할 수 없으므로 `provider_id`
 * 를 그대로 남기는 것과 다르다 - 탈퇴 시 개인 식별정보를 지우는 원칙([Member.anonymize])과 부딪히지
 * 않는 선을 이 해시가 만든다.
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
