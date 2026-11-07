package com.nexters.gamss.support

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.payment.apple.AppleTransaction
import com.nexters.gamss.payment.apple.AppleTransactionDecoder
import com.nexters.gamss.payment.domain.PaymentEnvironment
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Instant

/**
 * 통합 테스트에서 실제 서명 검증([com.nexters.gamss.payment.apple.SignedAppleTransactionDecoder]) 대신 쓰는
 * 가짜 구현. JWS 문자열마다 풀린 거래를 심어 두고, 심지 않은 문자열은 서명 검증 실패로 거부한다.
 * 실제 서명 검증은 `SignedAppleTransactionDecoderTest` 가 맡는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeAppleTransactionDecoderConfig {
    @Bean
    @Primary
    fun fakeAppleTransactionDecoder(): FakeAppleTransactionDecoder = FakeAppleTransactionDecoder()
}

class FakeAppleTransactionDecoder : AppleTransactionDecoder {
    private val transactions = mutableMapOf<String, AppleTransaction>()

    /** 검증이 일시적으로 실패하게 한다. */
    var unavailable = false

    fun stub(
        signedTransaction: String,
        transaction: AppleTransaction,
    ) {
        transactions[signedTransaction] = transaction
    }

    fun reset() {
        transactions.clear()
        unavailable = false
    }

    override fun decode(signedTransaction: String): AppleTransaction {
        if (unavailable) {
            throw BusinessException(ErrorCode.PAYMENT_STORE_UNAVAILABLE)
        }
        return transactions[signedTransaction] ?: throw BusinessException(ErrorCode.PURCHASE_INVALID)
    }

    companion object {
        fun transaction(
            accountToken: String?,
            transactionId: String? = "2000000123456789",
            productId: String? = "token_300000",
            type: String? = "Consumable",
            quantity: Int? = 1,
            environment: PaymentEnvironment = PaymentEnvironment.PRODUCTION,
            purchaseDate: Instant? = Instant.parse("2026-11-06T00:00:00Z"),
            revocationDate: Instant? = null,
        ) = AppleTransaction(
            transactionId = transactionId,
            productId = productId,
            type = type,
            quantity = quantity,
            appAccountToken = accountToken,
            purchaseDate = purchaseDate,
            revocationDate = revocationDate,
            environment = environment,
        )
    }
}
