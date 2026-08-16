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
     * ## 이미 있는 토큰의 소유자를 옮기는 이유
     *
     * FCM 토큰은 **계정이 아니라 앱 설치(기기)에 붙는다.** 로그인·로그아웃과 무관하게 유지되고,
     * 앱을 지웠다 깔아야 새로 발급된다. 그래서 **폰 하나를 두 계정이 차례로 쓰는 상황**이 생긴다:
     *
     * ```
     * 기기 1대, 토큰 T 하나                       device_tokens
     *   ① A 로그인   → 등록                        (A, T)
     *   ② A 로그아웃 → 앱이 해제 API 를 호출        (없음)
     *   ③ B 로그인   → 같은 T 를 다시 등록          (B, T)
     * ```
     *
     * 흔한 흐름은 아니지만 **②가 빠지면**(오프라인·크래시·구버전 앱) 서버는 ③에서 이미 A 것으로
     * 등록된 토큰을 다시 받게 된다. 계정을 갈아타는 경우 자체는 실제로 있다 — QA 가 테스트 계정을
     * 번갈아 쓰거나, 사용자가 다른 소셜 계정으로 갈아타거나, 탈퇴 후 재가입해 새 회원 번호를 받는
     * 경우다(폰을 팔거나 물려주는 경우는 앱을 재설치하므로 토큰이 달라져 해당 없다).
     *
     * 그때 선택지는 셋이고, 옮기는 것만 말이 된다.
     *
     * - **거절**: B 는 알림을 못 받는다. 그 기기의 토큰이 계속 A 것으로 남기 때문이다.
     * - **새 행 추가**: `(A, T)` 와 `(B, T)` 가 함께 남는다. 알림은 회원 앞으로 발송되므로 **A 에게
     *   보낸 알림이 T 로, 즉 지금 B 가 보고 있는 화면으로 뜬다.** 알림 문구에는 카드 한 줄이 들어가서
     *   남의 감정 기록이 잠금화면에 노출된다.
     * - **소유자 이동**(선택): 행은 하나로 유지되고, T 는 항상 마지막에 로그인한 사람의 것이 된다.
     *
     * 유니크를 `(member_id, token)` 이 아니라 **`token` 단독**으로 건 이유가 이것이다. 둘 다 "앱이
     * 실행마다 등록해도 행이 안 늘어난다"는 만족시키지만, 위의 `(A, T)`·`(B, T)` 공존을 막는 것은
     * `token` 단독 유니크뿐이다.
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
