package com.nexters.gamss.notification.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.notification.domain.FcmToken
import com.nexters.gamss.notification.repository.DeviceTokenRepository
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceTokenServiceTest : RepositoryTest() {
    @Autowired
    private lateinit var deviceTokenService: DeviceTokenService

    @Autowired
    private lateinit var deviceTokenRepository: DeviceTokenRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberService: MemberService

    @Test
    fun `토큰을 등록하면 회원의 기기로 저장된다`() {
        val member = memberRepository.save(Member("me@a.com"))

        deviceTokenService.register(member.id, FcmToken(TOKEN))

        val saved = deviceTokenRepository.findByToken(FcmToken(TOKEN))
        assertNotNull(saved)
        assertEquals(member.id, saved.memberId)
    }

    @Test
    fun `같은 토큰을 다시 등록해도 행이 늘지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        deviceTokenService.register(member.id, FcmToken(TOKEN))
        deviceTokenService.register(member.id, FcmToken(TOKEN))

        assertEquals(1, deviceTokenRepository.findAll().count { it.token == FcmToken(TOKEN) })
    }

    @Test
    fun `같은 기기를 다른 회원이 등록하면 소유자가 옮겨간다`() {
        val previous = memberRepository.save(Member("before@a.com"))
        val next = memberRepository.save(Member("after@a.com"))
        deviceTokenService.register(previous.id, FcmToken(TOKEN))

        deviceTokenService.register(next.id, FcmToken(TOKEN))

        val tokens = deviceTokenRepository.findAll().filter { it.token == FcmToken(TOKEN) }
        assertEquals(1, tokens.size)
        assertEquals(next.id, tokens.first().memberId)
    }

    @Test
    fun `기기를 여러 대 등록하면 모두 남는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        deviceTokenService.register(member.id, FcmToken("phone-token"))
        deviceTokenService.register(member.id, FcmToken("tablet-token"))

        assertEquals(2, deviceTokenRepository.findAll().count { it.isOwnedBy(member.id) })
    }

    /**
     * 토큰은 대소문자를 구분하는 값이라 대소문자만 다르면 다른 기기다. 컬럼 collation 을 못 박지
     * 않으면(V31) DB 기본값이 둘을 같은 유니크 키로 보고 한 행으로 합쳐, 뒤에 등록한 회원에게 남의
     * 토큰이 묶이고 앞 회원은 등록이 사라진다.
     */
    @Test
    fun `대소문자만 다른 토큰은 다른 기기로 취급한다`() {
        val upper = memberRepository.save(Member("upper@a.com"))
        val lower = memberRepository.save(Member("lower@a.com"))

        deviceTokenService.register(upper.id, FcmToken("AbC-token"))
        deviceTokenService.register(lower.id, FcmToken("abc-token"))

        assertEquals(upper.id, deviceTokenRepository.findByToken(FcmToken("AbC-token"))?.memberId)
        assertEquals(lower.id, deviceTokenRepository.findByToken(FcmToken("abc-token"))?.memberId)
    }

    @Test
    fun `해제하면 토큰이 지워진다`() {
        val member = memberRepository.save(Member("me@a.com"))
        deviceTokenService.register(member.id, FcmToken(TOKEN))

        deviceTokenService.unregister(member.id, FcmToken(TOKEN))

        assertNull(deviceTokenRepository.findByToken(FcmToken(TOKEN)))
    }

    @Test
    fun `없는 토큰을 해제해도 실패하지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        deviceTokenService.unregister(member.id, FcmToken("never-registered"))
    }

    @Test
    fun `남이 등록한 토큰은 해제되지 않는다`() {
        val owner = memberRepository.save(Member("owner@a.com"))
        val stranger = memberRepository.save(Member("stranger@a.com"))
        deviceTokenService.register(owner.id, FcmToken(TOKEN))

        deviceTokenService.unregister(stranger.id, FcmToken(TOKEN))

        val saved = deviceTokenRepository.findByToken(FcmToken(TOKEN))
        assertNotNull(saved)
        assertEquals(owner.id, saved.memberId)
    }

    @Test
    fun `탈퇴하면 등록된 기기가 함께 정리된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        deviceTokenService.register(member.id, FcmToken(TOKEN))

        memberService.withdraw(member.id)

        assertNull(deviceTokenRepository.findByToken(FcmToken(TOKEN)))
    }

    companion object {
        private const val TOKEN = "fMEk9dQvS0m1:APA91bH-device-token"
    }
}
