package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardShareProperties
import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.ShareToken
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardShareServiceTest {
    private val cardRepository = mockk<CardRepository>()
    private val shareTokenGenerator = mockk<ShareTokenGenerator>()
    private val service =
        CardShareService(
            cardRepository,
            shareTokenGenerator,
            CardShareProperties(baseUrl = "https://gamss.kr/c"),
        )

    private fun card(memberId: Long = MEMBER_ID): Card =
        Card(
            memberId = memberId,
            conversationId = 10L,
            emotion = EmotionType.ANGER,
            summary = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
            message = "우산을 안 챙겨서 옷이 다 젖어버렸어요",
            conversationCreatedAt = Instant.parse("2026-07-23T01:00:00Z"),
        )

    @Test
    fun `발급하면 base-url 뒤에 토큰을 붙인 링크를 준다`() {
        every { cardRepository.findByIdForUpdate(CARD_ID) } returns Optional.of(card())
        every { shareTokenGenerator.generate() } returns ShareToken(TOKEN)

        assertEquals("https://gamss.kr/c/$TOKEN", service.issueShareLink(MEMBER_ID, CARD_ID))
    }

    /** 다시 눌렀다고 링크가 바뀌면 앞서 보낸 링크가 죽는다. */
    @Test
    fun `이미 발급된 카드는 같은 링크를 그대로 돌려준다`() {
        val shared = card().apply { share(ShareToken(TOKEN)) }
        every { cardRepository.findByIdForUpdate(CARD_ID) } returns Optional.of(shared)
        every { shareTokenGenerator.generate() } returns ShareToken(OTHER_TOKEN)

        assertEquals("https://gamss.kr/c/$TOKEN", service.issueShareLink(MEMBER_ID, CARD_ID))
        assertEquals(TOKEN, shared.shareToken?.value, "새 토큰이 기존 토큰을 덮으면 안 된다")
    }

    @Test
    fun `남의 카드는 공유 링크를 발급할 수 없다`() {
        every { cardRepository.findByIdForUpdate(CARD_ID) } returns Optional.of(card(memberId = OTHER_MEMBER_ID))
        every { shareTokenGenerator.generate() } returns ShareToken(TOKEN)

        val thrown = assertFailsWith<BusinessException> { service.issueShareLink(MEMBER_ID, CARD_ID) }

        assertEquals(ErrorCode.CARD_ACCESS_DENIED, thrown.errorCode)
    }

    @Test
    fun `없는 카드는 발급할 수 없다`() {
        every { cardRepository.findByIdForUpdate(CARD_ID) } returns Optional.empty()

        val thrown = assertFailsWith<BusinessException> { service.issueShareLink(MEMBER_ID, CARD_ID) }

        assertEquals(ErrorCode.CARD_NOT_FOUND, thrown.errorCode)
    }

    /** 링크는 회수할 수 없다. 지운 카드에 토큰만 발급되고 열리지 않는 링크가 나가면 안 된다. */
    @Test
    fun `지운 카드는 공유 링크를 발급할 수 없다`() {
        every { cardRepository.findByIdForUpdate(CARD_ID) } returns Optional.of(card().apply { delete() })
        every { shareTokenGenerator.generate() } returns ShareToken(TOKEN)

        val thrown = assertFailsWith<BusinessException> { service.issueShareLink(MEMBER_ID, CARD_ID) }

        assertEquals(ErrorCode.CARD_ALREADY_DELETED, thrown.errorCode)
    }

    @Test
    fun `공유 토큰으로 카드를 연다`() {
        val shared = card()
        every { cardRepository.findVisibleByShareToken(TOKEN) } returns Optional.of(shared)

        assertEquals(shared, service.getSharedCard(TOKEN))
    }

    /** 링크 자리에 아무 문자열이나 넣는 요청이 곧바로 조회 부하가 되지 않게 한다. */
    @Test
    fun `형식이 틀린 토큰은 조회하지 않고 404 를 준다`() {
        val thrown = assertFailsWith<BusinessException> { service.getSharedCard("짧음") }

        assertEquals(ErrorCode.CARD_NOT_FOUND, thrown.errorCode)
        verify(exactly = 0) { cardRepository.findVisibleByShareToken(any()) }
    }

    /** 구분해서 알려주면 "이 토큰은 존재하긴 한다"는 사실이 샌다. */
    @Test
    fun `없는 토큰과 지워진 카드는 같은 404 다`() {
        every { cardRepository.findVisibleByShareToken(TOKEN) } returns Optional.empty()

        val thrown = assertFailsWith<BusinessException> { service.getSharedCard(TOKEN) }

        assertEquals(ErrorCode.CARD_NOT_FOUND, thrown.errorCode)
    }

    private companion object {
        const val MEMBER_ID = 1L
        const val OTHER_MEMBER_ID = 2L
        const val CARD_ID = 100L
        const val TOKEN = "Zm9vYmFyYmF6cXV4MTIzNA"
        const val OTHER_TOKEN = "AAAAbbbbCCCCddddEEEEff"
    }
}
