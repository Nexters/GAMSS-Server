package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.search.ConversationSearchPort
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationSearchServiceTest {
    private val searchPort = mockk<ConversationSearchPort>()
    private val service = ConversationSearchService(searchPort)

    @Test
    fun `검색어가 공백 제거 후 2자 미만이면 INVALID_INPUT 예외`() {
        val exception = assertFailsWith<BusinessException> { service.search(memberId = 1L, keyword = " 가 ", page = 0, size = 20) }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `검색어 앞뒤 공백을 제거해 포트에 위임한다`() {
        val keywordSlot = slot<String>()
        every { searchPort.search(1L, capture(keywordSlot), any()) } returns PageImpl(emptyList())

        service.search(memberId = 1L, keyword = "  짜증  ", page = 0, size = 20)

        assertEquals("짜증", keywordSlot.captured)
        verify(exactly = 1) { searchPort.search(1L, "짜증", any()) }
    }
}
