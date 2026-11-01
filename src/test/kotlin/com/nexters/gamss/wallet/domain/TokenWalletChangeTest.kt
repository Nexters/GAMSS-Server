package com.nexters.gamss.wallet.domain

import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class TokenWalletChangeTest {
    @Test
    fun `사용이 아닌 변경은 멱등 키가 있어야 한다`() {
        TokenLedgerType.entries.filter { it.requiresRefId }.forEach { type ->
            assertThrows<IllegalArgumentException>("$type") { TokenWalletChange(SUBJECT, type, TokenAmount(1), null) }
        }
    }

    @Test
    fun `사용은 멱등 키를 받지 않는다`() {
        assertThrows<IllegalArgumentException> {
            TokenWalletChange(SUBJECT, TokenLedgerType.CONSUME, TokenAmount(1), LedgerRefId("consume:1"))
        }
        assertDoesNotThrow { TokenWalletChange(SUBJECT, TokenLedgerType.CONSUME, TokenAmount(1), null) }
    }

    @Test
    fun `주체 키는 소문자 hex 64자만 받는다`() {
        listOf("a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64)).forEach { key ->
            assertThrows<IllegalArgumentException>(key) {
                TokenWalletChange(key, TokenLedgerType.PURCHASE, TokenAmount(1), LedgerRefId("purchase:1"))
            }
        }
    }

    @Test
    fun `토큰 양은 양수만 받는다`() {
        assertThrows<IllegalArgumentException> { TokenAmount(0) }
        assertThrows<IllegalArgumentException> { TokenAmount(-1) }
    }

    @Test
    fun `멱등 키는 비어 있거나 컬럼보다 길 수 없다`() {
        assertThrows<IllegalArgumentException> { LedgerRefId(" ") }
        assertThrows<IllegalArgumentException> { LedgerRefId("a".repeat(LedgerRefId.MAX_LENGTH + 1)) }
        assertDoesNotThrow { LedgerRefId("a".repeat(LedgerRefId.MAX_LENGTH)) }
    }

    private companion object {
        val SUBJECT = "a".repeat(64)
    }
}
