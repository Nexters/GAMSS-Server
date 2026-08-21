package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 탈퇴하면 그 사람의 공유 링크가 죽는지 본다.
 *
 * 인증이 필요한 경로는 accessToken 수명이 지나면 닫히지만 공유 링크는 스스로 닫히는 시점이
 * 없다. 여기가 깨지면 탈퇴한 사람의 감정 기록이 회수할 수 없는 주소로 계속 열린다.
 */
class WithdrawnMemberShareLinkCleanerTest : RepositoryTest() {
    @Autowired
    private lateinit var cardShareService: CardShareService

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberService: MemberService

    @Test
    fun `탈퇴하면 발급해 둔 공유 링크가 열리지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val token = shareLinkTokenOf(member.id)

        memberService.withdraw(member.id)

        val error = assertFailsWith<BusinessException> { cardShareService.getSharedCard(token) }
        assertEquals(ErrorCode.CARD_NOT_FOUND, error.errorCode)
    }

    @Test
    fun `탈퇴하면 토큰 자체가 회수된다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val cardId = savedCard(member.id).id
        cardShareService.issueShareLink(member.id, cardId)

        memberService.withdraw(member.id)

        assertNull(cardRepository.findById(cardId).get().shareToken)
    }

    @Test
    fun `탈퇴는 다른 회원의 공유 링크를 건드리지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val otherToken = shareLinkTokenOf(other.id)

        memberService.withdraw(member.id)

        assertEquals(otherToken, cardShareService.getSharedCard(otherToken).shareToken?.value)
    }

    /** 카드 한 장을 만들어 공유 링크를 발급하고, 링크에 담긴 토큰을 돌려준다. */
    private fun shareLinkTokenOf(memberId: Long): String {
        val shareUrl = cardShareService.issueShareLink(memberId, savedCard(memberId).id)
        return shareUrl.substringAfterLast("/")
    }

    private fun savedCard(memberId: Long): Card {
        val conversation = conversationRepository.save(Conversation(memberId).apply { end() })
        return cardRepository.save(
            Card(
                memberId = memberId,
                conversationId = conversation.id,
                emotion = EmotionType.ANGER,
                summary = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
                message = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
                conversationCreatedAt = Instant.parse("2026-07-23T01:00:00Z"),
            ),
        )
    }
}
