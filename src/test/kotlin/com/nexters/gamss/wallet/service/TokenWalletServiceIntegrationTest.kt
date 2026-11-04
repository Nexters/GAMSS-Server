package com.nexters.gamss.wallet.service

import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.TestcontainersConfig
import com.nexters.gamss.wallet.domain.LedgerRefId
import com.nexters.gamss.wallet.domain.TokenAmount
import com.nexters.gamss.wallet.domain.TokenLedgerType
import com.nexters.gamss.wallet.domain.TokenWalletChange
import com.nexters.gamss.wallet.repository.TokenLedgerRepository
import com.nexters.gamss.wallet.repository.TokenWalletRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class)
class TokenWalletServiceIntegrationTest {
    @Autowired
    private lateinit var walletService: TokenWalletService

    @Autowired
    private lateinit var walletReader: TokenWalletReader

    @Autowired
    private lateinit var walletRepository: TokenWalletRepository

    @Autowired
    private lateinit var ledgerRepository: TokenLedgerRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @AfterEach
    fun cleanUp() {
        ledgerRepository.deleteAll()
        walletRepository.deleteAll()
        identityRepository.deleteAll()
    }

    @Test
    fun `같은 멱등 키로 다시 반영하면 한 번만 반영되고 재요청으로 알려준다`() {
        val first = walletService.apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))
        val second = walletService.apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))

        assertFalse(first.replayed)
        assertTrue(second.replayed)
        assertEquals(3_000, second.appliedAmount)
        assertEquals(3_000, second.balance)
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `같은 멱등 키로 다른 양이 오면 거부하고 잔액은 그대로다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))

        assertThrows<IllegalStateException> { walletService.apply(change(TokenLedgerType.PURCHASE, 6_000, "purchase:1")) }

        assertEquals(3_000, walletRepository.findById(SUBJECT).get().balance)
        assertEquals(1, ledgerRepository.count())
    }

    @Test
    fun `대소문자만 다른 멱등 키는 다른 키로 본다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:abc"))

        val result = walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:ABC"))

        assertFalse(result.replayed)
        assertEquals(2_000, result.balance)
    }

    @Test
    fun `뒤 공백만 다른 멱등 키는 다른 키로 본다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:1"))

        val result = walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:1 "))

        assertFalse(result.replayed)
        assertEquals(2_000, result.balance)
        assertEquals(2, ledgerRepository.count())
    }

    @Test
    fun `회수로 음수가 된 잔액은 다음 지급에서 상계된다`() {
        walletService.apply(change(TokenLedgerType.PURCHASE, 1_000, "purchase:1"))
        walletService.apply(change(TokenLedgerType.REFUND, 3_000, "refund:1"))

        val result = walletService.apply(change(TokenLedgerType.ADMIN_GRANT, 2_500, "admin:1"))

        assertEquals(500, result.balance)
        assertEquals(listOf(1_000L, -2_000L, 500L), ledgerRepository.findAll().sortedBy { it.id }.map { it.balanceAfter })
    }

    @Test
    fun `산 적 없는 주체의 사용은 지갑을 만들지 않는다`() {
        val result = walletService.apply(change(TokenLedgerType.CONSUME, 100, null))

        assertEquals(0, result.appliedAmount)
        assertFalse(walletRepository.existsById(SUBJECT))
        assertEquals(0, ledgerRepository.count())
    }

    @Test
    fun `회원의 잔액은 주체 매핑으로 찾는다`() {
        identityRepository.upsert(MEMBER_ID, SUBJECT)
        walletService.apply(change(TokenLedgerType.PURCHASE, 3_000, "purchase:1"))

        assertEquals(3_000, walletReader.balanceOf(MEMBER_ID))
    }

    @Test
    fun `매핑이나 지갑이 없는 회원의 잔액은 0이다`() {
        assertEquals(0, walletReader.balanceOf(MEMBER_ID))

        identityRepository.upsert(MEMBER_ID, SUBJECT)

        assertEquals(0, walletReader.balanceOf(MEMBER_ID))
    }

    private fun change(
        type: TokenLedgerType,
        amount: Long,
        refId: String?,
    ) = TokenWalletChange(SUBJECT, type, TokenAmount(amount), refId?.let(::LedgerRefId))

    private companion object {
        const val MEMBER_ID = 1L
        val SUBJECT = "a".repeat(64)
    }
}
