package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.domain.Payment
import com.nexters.gamss.payment.domain.PaymentAccountToken
import com.nexters.gamss.payment.domain.PaymentEnvironment
import com.nexters.gamss.payment.domain.PaymentPlatform
import com.nexters.gamss.payment.domain.StoreTransactionKey
import com.nexters.gamss.payment.domain.VerifiedPurchase
import com.nexters.gamss.payment.repository.PaymentRepository
import com.nexters.gamss.payment.repository.TokenProductRepository
import com.nexters.gamss.wallet.domain.LedgerRefId
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.service.TokenWalletReader
import com.nexters.gamss.wallet.service.TokenWalletService
import org.slf4j.LoggerFactory
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.absoluteValue

/**
 * 지급과 회수의 트랜잭션 작업 단위. 결제 기록과 지갑 반영이 한 트랜잭션에서 함께 커밋된다.
 *
 * 경합 재시도는 트랜잭션 밖에서 해야 하므로 여기서 하지 않는다. 바깥 진입점은 [PurchaseGrantService] 다.
 */
@Service
class PaymentRecordService(
    private val paymentRepository: PaymentRepository,
    private val productRepository: TokenProductRepository,
    private val accountService: PaymentAccountService,
    private val walletService: TokenWalletService,
    private val walletReader: TokenWalletReader,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [requesterMemberId] 가 있으면 구매의 계정 토큰이 그 회원의 것이어야 한다. 웹훅 경로는 비교할
     * 회원이 없으므로 null 로 부르고, 계정 토큰으로 주인을 정한다.
     *
     * 같은 거래가 다시 오면 새로 지급하지 않고 처음 결과를 돌려준다. 그 판단을 상품 확인보다 먼저 하는
     * 이유는 지급 뒤에 상품이 판매 중지돼도 재요청이 실패로 바뀌면 안 되기 때문이다.
     */
    @Transactional
    fun grant(
        purchase: VerifiedPurchase,
        requesterMemberId: Long?,
    ): GrantResult {
        val subjectKey = ownerOf(purchase, requesterMemberId)
        val existing = paymentRepository.findByPlatformAndStoreTransactionKey(purchase.platform, purchase.storeTransactionKey.value)
        if (existing != null) {
            return replay(existing, purchase, subjectKey)
        }
        val payment = save(purchase, subjectKey, grantedTokensOf(purchase, subjectKey))
        val result = walletService.apply(purchaseChange(payment))
        return GrantResult(paymentId = payment.id, grantedTokens = payment.grantedTokens, balance = result.balance, replayed = false)
    }

    /**
     * 이 결제에서 지금까지 회수됐어야 할 총량을 [targetRefundedTokens] 로 맞춘다. 결제 행을 잠그고
     * 차이만 원장에 반영하므로, 같은 목표가 겹쳐 와도 두 번째는 아무것도 하지 않는다.
     *
     * 이미 반영한 출처([sourceId])가 다시 오면 목표와 관계없이 아무것도 하지 않는다. 환불 뒤 환불 취소까지
     * 반영된 상태에서 옛 환불 알림이 다시 오면, 목표만 보고는 다시 회수해야 하는 것처럼 보인다. 그런데
     * 원장은 같은 멱등 키를 재요청으로 보고 잔액을 그대로 두므로, 결제 기록만 바뀌어 둘이 어긋난다.
     */
    @Transactional
    fun applyRefund(
        platform: PaymentPlatform,
        storeTransactionKey: StoreTransactionKey,
        targetRefundedTokens: Long,
        sourceId: String,
    ): RefundResult {
        val payment =
            paymentRepository.findByPlatformAndStoreTransactionKeyForUpdate(platform, storeTransactionKey.value)
                ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)
        val refId = LedgerRefId(payment.refundRefId(sourceId))
        if (walletReader.isApplied(refId)) {
            return RefundResult(paymentId = payment.id, refundedTokens = payment.refundedTokens, changedTokens = 0)
        }
        val delta = payment.refundTo(targetRefundedTokens)
        if (delta != 0L) {
            val result = walletService.apply(refundChange(payment, delta, refId))
            check(!result.replayed) { "반영 이력이 없던 환불 출처가 재요청으로 처리됐습니다: refId=$refId" }
        }
        return RefundResult(paymentId = payment.id, refundedTokens = payment.refundedTokens, changedTokens = delta)
    }

    /**
     * 거부하면 결제 기록이 남지 않으므로 로그가 그 구매의 유일한 흔적이다. 계정 토큰이 없거나 발급한 적
     * 없는 값이면 돈은 나갔는데 받을 사람을 모르는 상태라 ERROR 로 남긴다. 다른 주체의 토큰이면 그
     * 토큰의 주인에게는 웹훅 경로로 지급될 수 있고, 남의 영수증을 보낸 것일 수도 있어 WARN 으로 둔다.
     *
     * 요청한 회원의 주체 매핑이 없으면 그보다 먼저 [PaymentAccountService.requesterSubjectKey] 가
     * 거부한다. 탈퇴 회원이거나 서버 쪽 문제라 이 구매의 주인 판단과는 별개다.
     *
     * Google RTDN 으로 온 테스트 구매(SANDBOX)인데 계정 토큰이 형식은 맞고 주인만 모르면 WARN 으로 낮춘다. RTDN 토픽은
     * 패키지당 하나라 prod 구독도 dev 빌드에서 한 라이선스 테스터 구매를 받는데, 계정 토큰은 환경마다 따로 발급되므로
     * prod 에서는 주인을 찾지 못한다. 실제 돈이 오가지 않는 이 구매가 ERROR 에 섞이면 진짜 수동 처리 건을 가린다.
     * Apple 은 환경마다 알림 URL 이 따로라 사정이 달라 넣지 않았다. 필요하면 Apple 알림(#245)에서 정한다.
     */
    private fun ownerOf(
        purchase: VerifiedPurchase,
        requesterMemberId: Long?,
    ): String {
        val owner = accountService.ownerOf(purchase.accountToken)
        val requester = requesterMemberId?.let { accountService.requesterSubjectKey(it) }
        if (owner == null) {
            if (isOtherEnvironmentTestPurchase(purchase, requesterMemberId)) {
                log.warn(
                    "주인을 모르는 테스트 구매라 지급하지 않았습니다. 같은 RTDN 토픽을 쓰는 다른 환경(dev 빌드)의 구매일 수 있습니다: " +
                        "platform={}, key={}, storeOrderId={}, productId={}, environment={}",
                    purchase.platform,
                    purchase.storeTransactionKey,
                    purchase.storeOrderId,
                    purchase.productId,
                    purchase.environment,
                )
            } else {
                log.error(
                    "계정 토큰으로 주인을 찾지 못해 지급하지 않았습니다. 수동 처리가 필요합니다: " +
                        "platform={}, key={}, storeOrderId={}, productId={}, quantity={}, environment={}, accountTokenPresent={}, " +
                        "requesterMemberId={}",
                    purchase.platform,
                    purchase.storeTransactionKey,
                    purchase.storeOrderId,
                    purchase.productId,
                    purchase.quantity,
                    purchase.environment,
                    purchase.accountToken != null,
                    requesterMemberId,
                )
            }
            throw BusinessException(if (requester == null) ErrorCode.PAYMENT_ACCOUNT_NOT_FOUND else ErrorCode.PURCHASE_ACCESS_DENIED)
        }
        if (requester != null && owner != requester) {
            log.warn(
                "다른 주체의 계정 토큰이 담긴 구매라 지급하지 않았습니다: platform={}, key={}, storeOrderId={}, requesterMemberId={}",
                purchase.platform,
                purchase.storeTransactionKey,
                purchase.storeOrderId,
                requesterMemberId,
            )
            throw BusinessException(ErrorCode.PURCHASE_ACCESS_DENIED)
        }
        return owner
    }

    /** Google 스토어 알림 경로이고, 테스트 구매이고, 어느 GAMSS 환경이 발급한 형식의 계정 토큰이다. */
    private fun isOtherEnvironmentTestPurchase(
        purchase: VerifiedPurchase,
        requesterMemberId: Long?,
    ): Boolean =
        requesterMemberId == null &&
            purchase.platform == PaymentPlatform.GOOGLE &&
            purchase.environment == PaymentEnvironment.SANDBOX &&
            PaymentAccountToken.parseOrNull(purchase.accountToken) != null

    private fun replay(
        existing: Payment,
        purchase: VerifiedPurchase,
        subjectKey: String,
    ): GrantResult {
        check(existing.isSamePurchase(subjectKey, purchase.productId, purchase.quantity)) {
            "같은 거래에 다른 구매 내용이 들어왔습니다: platform=${purchase.platform}, key=${purchase.storeTransactionKey}"
        }
        val result = walletService.apply(purchaseChange(existing))
        return GrantResult(paymentId = existing.id, grantedTokens = existing.grantedTokens, balance = result.balance, replayed = true)
    }

    /**
     * 돈은 나갔는데 지급할 상품이 없는 상태라 수동 처리 대상이다. 그래서 ERROR 로 남긴다.
     *
     * 결제 기록을 남기지 않으므로 로그만 보고 처리할 수 있어야 한다. Google 거래 키는 해시라 스토어에서
     * 조회할 수 없어서 [VerifiedPurchase.storeOrderId] 를 함께 남기고, 받을 사람을 [subjectKey] 로 남긴다.
     */
    private fun grantedTokensOf(
        purchase: VerifiedPurchase,
        subjectKey: String,
    ): Long {
        val product = productRepository.findByIdOrNull(purchase.productId)
        if (product == null || !product.active) {
            log.error(
                "판매 중이 아닌 상품의 결제라 지급하지 않았습니다. 수동 처리가 필요합니다: " +
                    "platform={}, key={}, storeOrderId={}, productId={}, quantity={}, found={}, subjectKey={}",
                purchase.platform,
                purchase.storeTransactionKey,
                purchase.storeOrderId,
                purchase.productId,
                purchase.quantity,
                product != null,
                subjectKey,
            )
            throw BusinessException(ErrorCode.PAYMENT_PRODUCT_UNAVAILABLE)
        }
        return product.tokensFor(purchase.quantity)
    }

    private fun save(
        purchase: VerifiedPurchase,
        subjectKey: String,
        grantedTokens: Long,
    ): Payment {
        val payment =
            Payment(
                platform = purchase.platform,
                storeTransactionKey = purchase.storeTransactionKey.value,
                storePurchaseToken = purchase.storePurchaseToken,
                storeOrderId = purchase.storeOrderId,
                subjectKey = subjectKey,
                productId = purchase.productId,
                quantity = purchase.quantity,
                grantedTokens = grantedTokens,
                environment = purchase.environment,
                purchasedAt = purchase.purchasedAt,
            )
        try {
            return paymentRepository.saveAndFlush(payment)
        } catch (e: DataIntegrityViolationException) {
            throw PaymentConflictException(purchase.platform, purchase.storeTransactionKey, e)
        } catch (e: ConcurrencyFailureException) {
            throw PaymentConflictException(purchase.platform, purchase.storeTransactionKey, e)
        }
    }

    private fun purchaseChange(payment: Payment): TokenWalletChange =
        TokenWalletChange(
            subjectKey = payment.subjectKey,
            type = TokenLedgerType.PURCHASE,
            amount = TokenAmount(payment.grantedTokens),
            refId = LedgerRefId(payment.purchaseRefId()),
        )

    private fun refundChange(
        payment: Payment,
        delta: Long,
        refId: LedgerRefId,
    ): TokenWalletChange =
        TokenWalletChange(
            subjectKey = payment.subjectKey,
            type = if (delta > 0) TokenLedgerType.REFUND else TokenLedgerType.REFUND_REVERSED,
            amount = TokenAmount(delta.absoluteValue),
            refId = refId,
        )
}
