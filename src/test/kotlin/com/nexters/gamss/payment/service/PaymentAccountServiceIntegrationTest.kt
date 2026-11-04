package com.nexters.gamss.payment.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PaymentAccountServiceIntegrationTest : RepositoryTest() {
    @Autowired
    private lateinit var accountService: PaymentAccountService

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `계정 토큰은 같은 회원에게 항상 같고 다른 주체와는 다르다`() {
        identityRepository.upsert(BUYER, BUYER_SUBJECT)
        identityRepository.upsert(OTHER, OTHER_SUBJECT)

        val first = accountService.accountTokenOf(BUYER)

        assertEquals(first, accountService.accountTokenOf(BUYER))
        assertNotEquals(first, accountService.accountTokenOf(OTHER))
    }

    @Test
    fun `같은 주체에 붙은 다른 회원 번호도 같은 계정 토큰을 받는다`() {
        identityRepository.upsert(BUYER, BUYER_SUBJECT)
        val first = accountService.accountTokenOf(BUYER)

        identityRepository.upsert(REJOINED, BUYER_SUBJECT)

        assertEquals(first, accountService.accountTokenOf(REJOINED))
    }

    @Test
    fun `스토어가 대문자로 돌려준 계정 토큰도 같은 주인을 찾는다`() {
        identityRepository.upsert(BUYER, BUYER_SUBJECT)
        val token = accountService.accountTokenOf(BUYER).value

        assertEquals(BUYER_SUBJECT, accountService.ownerOf(token.uppercase()))
    }

    @Test
    fun `형식이 틀리거나 발급한 적 없는 계정 토큰은 주인이 없다`() {
        listOf(null, "not-a-uuid", "3f2b8c1e-7a4d-4e6b-9c0a-1d2e3f4a5b6c").forEach { token ->
            assertNull(accountService.ownerOf(token), "token=$token")
        }
    }

    @Test
    fun `탈퇴로 매핑이 지워진 회원은 탈퇴한 회원으로 거부된다`() {
        val withdrawn = memberRepository.save(Member("withdrawn@a.com").apply { withdraw() })

        val error = assertFailsWith<BusinessException> { accountService.accountTokenOf(withdrawn.id) }

        assertEquals(ErrorCode.WITHDRAWN_MEMBER, error.errorCode)
    }

    @Test
    fun `주체 매핑이 없는 활성 회원은 서버 오류로 올린다`() {
        val active = memberRepository.save(Member("active@a.com"))

        assertFailsWith<IllegalStateException> { accountService.accountTokenOf(active.id) }
    }

    private companion object {
        const val BUYER = 9_001L
        const val OTHER = 9_002L
        const val REJOINED = 9_003L
        val BUYER_SUBJECT = "a".repeat(64)
        val OTHER_SUBJECT = "b".repeat(64)
    }
}
