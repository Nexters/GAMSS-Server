package com.nexters.gamss.wallet.domain

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenWalletTest {
    @Test
    fun `지급하면 잔액이 늘고 원장에 증가분과 반영 후 잔액이 남는다`() {
        val wallet = TokenWallet(SUBJECT, balance = 100)

        val entry = wallet.apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))!!

        assertEquals(3_100, wallet.balance)
        assertEquals(3_000, entry.amount)
        assertEquals(3_100, entry.balanceAfter)
        assertEquals("purchase:1", entry.refId)
    }

    @Test
    fun `회수는 잔액을 음수로 만들 수 있다`() {
        val wallet = TokenWallet(SUBJECT, balance = 500)

        val entry = wallet.apply(change(TokenLedgerType.REFUND, 3_000, "refund:1"))!!

        assertEquals(-2_500, wallet.balance)
        assertEquals(-3_000, entry.amount)
    }

    @Test
    fun `음수 잔액에 지급하면 상계된다`() {
        val wallet = TokenWallet(SUBJECT, balance = -2_500)

        wallet.apply(change(TokenLedgerType.ADMIN_GRANT, 3_000, "admin:1"))

        assertEquals(500, wallet.balance)
    }

    @Test
    fun `사용은 잔액까지만 깎고 넘친 만큼은 버린다`() {
        val wallet = TokenWallet(SUBJECT, balance = 1_000)

        val entry = wallet.apply(change(TokenLedgerType.CONSUME, 3_000, null))!!

        assertEquals(0, wallet.balance)
        assertEquals(-1_000, entry.amount)
    }

    @Test
    fun `잔액이 0 이하면 사용해도 바뀌지 않고 원장도 남지 않는다`() {
        val empty = TokenWallet(SUBJECT, balance = 0)
        val negative = TokenWallet(SUBJECT, balance = -500)

        assertNull(empty.apply(change(TokenLedgerType.CONSUME, 100, null)))
        assertNull(negative.apply(change(TokenLedgerType.CONSUME, 100, null)))
        assertEquals(0, empty.balance)
        assertEquals(-500, negative.balance)
    }

    @Test
    fun `다른 주체의 변경은 반영하지 않는다`() {
        val wallet = TokenWallet(SUBJECT)

        assertThrows<IllegalArgumentException> {
            wallet.apply(TokenWalletChange("b".repeat(64), TokenLedgerType.PURCHASE, TokenAmount(1), LedgerRefId("purchase:1")))
        }
    }

    @Test
    fun `잔액이 넘치면 반영하지 않는다`() {
        val wallet = TokenWallet(SUBJECT, balance = Long.MAX_VALUE)

        assertThrows<ArithmeticException> { wallet.apply(change(TokenLedgerType.PURCHASE, 1, "purchase:1")) }
        assertEquals(Long.MAX_VALUE, wallet.balance)
    }

    @Test
    fun `같은 변경의 재요청인지 주체와 타입과 양으로 판단한다`() {
        val entry = TokenWallet(SUBJECT).apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))!!

        assertTrue(entry.isReplayOf(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1")))
        assertFalse(entry.isReplayOf(change(TokenLedgerType.PURCHASE, 6_000, "purchase:1")))
        assertFalse(entry.isReplayOf(change(TokenLedgerType.ADMIN_GRANT, 3_000, "purchase:1")))
    }

    private fun change(
        type: TokenLedgerType,
        amount: Long,
        refId: String?,
    ) = TokenWalletChange(SUBJECT, type, TokenAmount(amount), refId?.let(::LedgerRefId))

    private companion object {
        val SUBJECT = "a".repeat(64)
    }
}
