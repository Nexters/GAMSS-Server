package com.nexters.gamss.wallet.domain

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaidTokenBalanceTest {
    @Test
    fun `지급하면 증가분과 반영 후 잔액이 원장에 남는다`() {
        val entry = balance(100).apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))!!

        assertEquals(3_000, entry.amount)
        assertEquals(3_100, entry.balanceAfter)
        assertEquals("purchase:1", entry.refId)
    }

    @Test
    fun `회수는 잔액을 음수로 만들 수 있다`() {
        val entry = balance(500).apply(change(TokenLedgerType.REFUND, 3_000, "refund:1"))!!

        assertEquals(-3_000, entry.amount)
        assertEquals(-2_500, entry.balanceAfter)
    }

    @Test
    fun `음수 잔액에 지급하면 상계된다`() {
        val entry = balance(-2_500).apply(change(TokenLedgerType.ADMIN_GRANT, 3_000, "admin:1"))!!

        assertEquals(500, entry.balanceAfter)
    }

    @Test
    fun `사용은 잔액까지만 깎고 넘친 만큼은 버린다`() {
        val entry = balance(1_000).apply(change(TokenLedgerType.CONSUME, 3_000, null))!!

        assertEquals(-1_000, entry.amount)
        assertEquals(0, entry.balanceAfter)
    }

    @Test
    fun `잔액이 0 이하면 사용해도 원장이 남지 않는다`() {
        assertNull(balance(0).apply(change(TokenLedgerType.CONSUME, 100, null)))
        assertNull(balance(-500).apply(change(TokenLedgerType.CONSUME, 100, null)))
    }

    @Test
    fun `다른 주체의 변경은 반영하지 않는다`() {
        assertThrows<IllegalArgumentException> {
            balance(0).apply(TokenWalletChange("b".repeat(64), TokenLedgerType.PURCHASE, TokenAmount(1), LedgerRefId("purchase:1")))
        }
    }

    @Test
    fun `잔액이 넘치면 반영하지 않는다`() {
        assertThrows<ArithmeticException> { balance(Long.MAX_VALUE).apply(change(TokenLedgerType.PURCHASE, 1, "purchase:1")) }
    }

    @Test
    fun `같은 변경의 재요청인지 주체와 타입과 양으로 판단한다`() {
        val entry = balance(0).apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))!!

        assertTrue(entry.isReplayOf(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1")))
        assertFalse(entry.isReplayOf(change(TokenLedgerType.PURCHASE, 6_000, "purchase:1")))
        assertFalse(entry.isReplayOf(change(TokenLedgerType.ADMIN_GRANT, 3_000, "purchase:1")))
        assertFalse(entry.isReplayOf(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1", "b".repeat(64))))
    }

    private fun balance(value: Long) = PaidTokenBalance(SUBJECT, value)

    private fun change(
        type: TokenLedgerType,
        amount: Long,
        refId: String?,
        subjectKey: String = SUBJECT,
    ) = TokenWalletChange(subjectKey, type, TokenAmount(amount), refId?.let(::LedgerRefId))

    private companion object {
        val SUBJECT = "a".repeat(64)
    }
}
