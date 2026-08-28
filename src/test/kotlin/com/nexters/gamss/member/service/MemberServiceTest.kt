package com.nexters.gamss.member.service

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemberServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val cleaner = RecordingCleaner()
    private val memberService = MemberService(memberRepository, WithdrawnMemberCleaners(listOf(cleaner)))

    @Test
    fun `회원을 생성하면 닉네임 초기값은 이름이다`() {
        every { memberRepository.save(any()) } answers { firstArg() }

        val member = memberService.create("a@example.com", "홍길동")

        assertEquals("a@example.com", member.email)
        assertEquals("홍길동", member.name)
        assertEquals(Nickname("홍길동"), member.nickname)
        verify(exactly = 1) { memberRepository.save(any()) }
    }

    @Test
    fun `이름이 없으면 이름·닉네임 없이 생성한다`() {
        every { memberRepository.save(any()) } answers { firstArg() }

        val member = memberService.create("a@example.com", null)

        assertNull(member.name)
        assertNull(member.nickname)
    }

    @Test
    fun `이름이 닉네임 규칙에 어긋나면 이름만 저장하고 닉네임은 비워 둔다`() {
        every { memberRepository.save(any()) } answers { firstArg() }

        val member = memberService.create("a@example.com", "김")

        assertEquals("김", member.name)
        assertNull(member.nickname)
    }

    @Test
    fun `getById로 회원을 조회한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        assertSame(member, memberService.getById(10L))
    }

    @Test
    fun `없는 회원을 조회하면 MEMBER_NOT_FOUND`() {
        every { memberRepository.findById(99L) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { memberService.getById(99L) }

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `닉네임을 수정한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        val updated = memberService.updateNickname(10L, Nickname("바다"))

        assertSame(member, updated)
        assertEquals(Nickname("바다"), updated.nickname)
    }

    @Test
    fun `회원을 탈퇴 처리한다`() {
        val member = Member("b@example.com")
        every { memberRepository.findById(10L) } returns Optional.of(member)

        memberService.withdraw(10L)

        assertEquals(true, member.isWithdrawn())
        assertEquals(listOf(10L), cleaner.cleaned, "탈퇴에 딸린 자원 정리가 호출돼야 한다")
    }

    @Test
    fun `이미 탈퇴한 회원이면 자원 정리를 호출하지 않는다`() {
        val member = Member("b@example.com").apply { withdraw() }
        every { memberRepository.findById(10L) } returns Optional.of(member)

        assertFailsWith<BusinessException> { memberService.withdraw(10L) }

        assertTrue(cleaner.cleaned.isEmpty())
    }

    @Test
    fun `이미 탈퇴한 회원을 다시 탈퇴하면 ALREADY_WITHDRAWN`() {
        val member = Member("b@example.com").apply { withdraw() }
        every { memberRepository.findById(10L) } returns Optional.of(member)

        val exception = assertFailsWith<BusinessException> { memberService.withdraw(10L) }

        assertEquals(ErrorCode.ALREADY_WITHDRAWN, exception.errorCode)
    }

    /** 정리 확장점이 어떤 memberId 로 불렸는지만 기록하는 페이크. */
    private class RecordingCleaner : WithdrawnMemberCleaner {
        val cleaned = mutableListOf<Long>()

        override fun clean(memberId: Long) {
            cleaned.add(memberId)
        }
    }
}
