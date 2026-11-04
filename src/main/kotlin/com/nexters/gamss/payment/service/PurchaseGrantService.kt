package com.nexters.gamss.payment.service

import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 검증된 구매의 지급과 환불 회수 진입점. 스토어 검증(#243~#246)은 검증만 하고 여기로 넘긴다.
 *
 * 같은 구매를 앱 재시도, 웹훅, 대사 배치가 여러 번 들고 와도 한 번만 지급된다. 동시에 오면 결제 기록의
 * 유니크 제약이 하나를 실패시키고, 그 트랜잭션은 rollback-only 라 그 자리에서 회복할 수 없다. 그래서
 * 트랜잭션 밖인 여기서 [ConflictRetry] 로 작업 전체를 다시 실행하고, 다시 실행하면 앞선 지급을 찾아
 * 재요청으로 끝난다. 지갑 반영([com.nexters.gamss.wallet.service.TokenWalletService])도 같은 이유로
 * 가장 바깥에서 감싸야 한다.
 *
 * **트랜잭션 안에서 부를 수 없다.** 부르는 쪽 트랜잭션에 참여하면 두 가지가 깨진다. 첫째, 재시도가
 * 같은 rollback-only 트랜잭션 안에서 돌아 아무것도 회복하지 못하고 결국 예외로 끝난다. 둘째, 부르는
 * 쪽이 결제를 먼저 읽어 두면 회수의 잠금 조회가 행은 잠그지만 영속성 컨텍스트에 남은 낡은 회수량을
 * 돌려준다. 그 사이 다른 요청이 회수를 커밋했다면 같은 양을 한 번 더 회수하는데, 이쪽은 예외 없이
 * 끝나 테스트로도 드러나지 않는다. 그래서 시작할 때 직접 확인하고 거부한다.
 */
@Service
class PurchaseGrantService(
    private val recordService: PaymentRecordService,
    private val conflictRetry: ConflictRetry,
) {
    /** 앱이 보낸 구매. 구매에 실린 계정 토큰이 [memberId] 의 것이 아니면 지급하지 않는다. */
    fun grant(
        memberId: Long,
        purchase: VerifiedPurchase,
    ): GrantResult {
        ensureOutsideTransaction()
        return conflictRetry.execute { recordService.grant(purchase, memberId) }
    }

    /** 스토어 알림으로만 들어온 구매(#245, #246). 비교할 회원이 없으므로 계정 토큰으로 주인을 정한다. */
    fun grantFromStoreNotification(purchase: VerifiedPurchase): GrantResult {
        ensureOutsideTransaction()
        return conflictRetry.execute { recordService.grant(purchase, null) }
    }

    /**
     * 전액 환불은 지급량, 부분 환불은 비율이나 수량만큼, 환불 취소는 0 을 [targetRefundedTokens] 로 넘긴다.
     *
     * [sourceId] 는 환불을 알려 온 출처(웹훅 알림 ID 등)이고 원장 멱등 키에 들어간다. 출처 하나는 변동
     * 하나에만 써야 한다 - 이미 반영한 출처가 다시 오면 목표와 관계없이 아무것도 하지 않는다. 멱등 키가
     * 128자 안에 들어가야 하므로, Google 의 64자 거래 키와 함께 쓰면 [sourceId] 는 49자까지다.
     *
     * 호출부가 맡아야 할 일이 둘 있다.
     * - **출처 중복 제거.** 여기서 기억하는 출처는 잔액을 바꾼 출처뿐이다. 목표가 현재 회수량과 같아
     *   차이 0 으로 끝난 출처는 원장에 아무것도 남지 않는다(원장은 0 인 변동을 받지 않는다). 그런 출처가
     *   환불 취소 뒤에 다시 오면 새 출처처럼 다시 회수된다. 그래서 처리한 출처는 결과와 관계없이 호출부가
     *   기록하고, 이미 기록한 출처는 여기까지 보내지 않는다.
     * - **도착 순서.** 목표값 방식은 마지막으로 도착한 목표를 사실로 본다. 환불 알림이 환불 취소 알림보다
     *   늦게 도착하면 다시 회수되고, 여기서는 그 순서를 가려내지 못한다. 알림 본문의 값보다 스토어를 다시
     *   조회한 현재 상태로 목표를 계산하면 어긋날 여지가 줄어든다.
     */
    fun applyRefund(
        platform: PaymentPlatform,
        storeTransactionKey: StoreTransactionKey,
        targetRefundedTokens: Long,
        sourceId: String,
    ): RefundResult {
        ensureOutsideTransaction()
        return conflictRetry.execute {
            recordService.applyRefund(platform, storeTransactionKey, targetRefundedTokens, sourceId)
        }
    }

    private fun ensureOutsideTransaction() {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "지급과 회수는 트랜잭션 안에서 부를 수 없습니다 - 경합 재시도가 무력화되고 낡은 회수량으로 이중 회수할 수 있습니다."
        }
    }
}
