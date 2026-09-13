package com.nexters.gamss.member.service

import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 매핑은 로그인마다 불린다. 두 번 불러도 같은 결과여야 하고, 로그인을 깨뜨리지 않아야 한다. */
@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class MemberSocialIdentityIntegrationTest {
    @Autowired
    private lateinit var service: MemberSocialIdentityService

    @Autowired
    private lateinit var repository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var subjectKeyGenerator: SubjectKeyGenerator

    @Test
    fun `매핑을 만든다`() {
        val member = memberRepository.save(Member("map@a.com"))

        service.ensureMapped(member.id, "GOOGLE", "sub-1")

        assertTrue(repository.existsByMemberId(member.id))
        assertEquals(subjectKeyGenerator.generate("GOOGLE", "sub-1"), repository.findById(member.id).orElseThrow().subjectKey)
    }

    /** 유니크 위반을 예외로 올리면 부르는 쪽 트랜잭션이 rollback-only 가 되어 로그인이 실패한다. */
    @Test
    fun `두 번 불러도 예외 없이 그대로다`() {
        val member = memberRepository.save(Member("twice@a.com"))

        service.ensureMapped(member.id, "GOOGLE", "sub-1")
        service.ensureMapped(member.id, "GOOGLE", "sub-1")

        assertEquals(1, repository.count())
    }

    /** 먼저 커밋한 값을 덮지 않는다. 같은 신원이면 주체 키도 같으므로 결과는 어차피 동일하다. */
    @Test
    fun `이미 있으면 주체 키를 갈아치우지 않는다`() {
        val member = memberRepository.save(Member("keep@a.com"))
        service.ensureMapped(member.id, "GOOGLE", "sub-1")

        service.ensureMapped(member.id, "APPLE", "sub-2")

        assertEquals(subjectKeyGenerator.generate("GOOGLE", "sub-1"), repository.findById(member.id).orElseThrow().subjectKey)
    }
}
