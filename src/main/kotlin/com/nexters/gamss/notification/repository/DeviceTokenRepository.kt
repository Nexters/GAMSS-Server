package com.nexters.gamss.notification.repository

import com.nexters.gamss.notification.domain.DeviceToken
import com.nexters.gamss.notification.domain.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    /**
     * 토큰을 [memberId] 소유로 만든다. 없으면 넣고, 이미 있으면 소유자를 옮긴다.
     *
     * 조회 후 저장/수정으로 나누지 않고 한 문장으로 처리하는 이유는 **중복 등록 경합** 때문이다.
     * 앱은 실행할 때마다 같은 토큰을 다시 등록하고, 재시도나 연타로 같은 요청이 겹칠 수 있다.
     * 조회-후-저장은 두 요청이 모두 "없음"을 보고 각자 insert 해 유니크 제약에 걸린다 — 그 예외는
     * flush 시점에 터져 트랜잭션이 이미 롤백 대상이라 그 자리에서 되돌릴 수도 없다.
     *
     * 소유자를 **옮기는** 것이 맞는 동작이다: 같은 기기에서 A 가 로그아웃하고 B 가 로그인하면 같은
     * 토큰이 B 로 온다. 거절하면 B 는 알림을 못 받고, 새 행을 만들면 A 에게 B 의 알림이 간다.
     *
     * `ON DUPLICATE KEY UPDATE` 는 MySQL 문법이다. 이 프로젝트는 MySQL 전용이라(flyway-mysql,
     * mysql-connector) 감수하고 쓴다.
     *
     * `flushAutomatically` 는 대기 중인 변경을 이 문장보다 먼저 내보낸다. 한 트랜잭션에서 해제하고
     * 다시 등록하면(권한을 껐다 켜는 흐름) 대기 중인 delete 가 이 insert 뒤로 밀려 유니크 제약에
     * 걸린다.
     *
     * `clearAutomatically` 는 **일부러 켜지 않았다**. 이 문장은 엔티티를 로드하지 않으므로 스스로
     * 만들어내는 낡은 엔티티가 없다. 반면 clear 의 효과는 이 리포지토리가 아니라 **트랜잭션의
     * 영속성 컨텍스트 전체**라, 이 호출이 더 큰 트랜잭션 안으로 들어가는 날 호출자가 들고 있던
     * 엔티티가 전부 준영속이 되고 그 뒤의 변경은 조용히 사라진다(flush 가 지켜주는 것은 호출
     * 이전 변경분까지다).
     *
     * 대신 감수하는 것: 같은 트랜잭션에서 이 토큰을 **이미 읽어둔 뒤** 소유자가 옮겨가면, 이후
     * 조회가 1차 캐시의 옛 `memberId` 를 돌려준다. 그런 경로는 지금 없다 — 생기면 그 지점에서
     * 다시 읽도록 하거나 여기서 clear 를 켜는 대신, 그 트랜잭션의 범위를 먼저 의심하는 게 맞다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value =
            "insert into device_tokens (member_id, token, created_at, updated_at) " +
                "values (:memberId, :token, now(6), now(6)) " +
                "on duplicate key update member_id = :memberId, updated_at = now(6)",
        nativeQuery = true,
    )
    fun upsert(
        @Param("memberId") memberId: Long,
        @Param("token") token: String,
    )

    /**
     * 본인 기기의 토큰만 지운다. 남의 토큰을 지우지 못하게 `member_id` 를 조건에 넣는다 —
     * 토큰 값만 알면 남의 알림을 끊을 수 있게 되면 안 된다.
     *
     * 지울 행이 없어도 예외 없이 no-op 이다. 이미 지웠거나 다른 계정으로 재등록된 토큰을 앱이 다시
     * 해제 요청해도 정상 흐름이다.
     */
    fun deleteByMemberIdAndToken(
        memberId: Long,
        token: FcmToken,
    )

    fun findByToken(token: FcmToken): DeviceToken?

    /** 탈퇴 정리. 회원이 사라지면 그 기기로 보낼 알림도 없다. */
    fun deleteByMemberId(memberId: Long)
}
