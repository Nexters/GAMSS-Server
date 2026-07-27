package com.nexters.gamss.conversation.domain

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationTitleTest {
    @Test
    fun `유효한 값으로 제목을 생성한다`() {
        assertEquals("비 오는 날의 짜증", ConversationTitle("비 오는 날의 짜증").value)
    }

    @Test
    fun `앞뒤 공백을 제거한다`() {
        assertEquals("제목", ConversationTitle("  제목  ").value)
    }

    @Test
    fun `최대 길이까지 허용한다`() {
        val value = "가".repeat(ConversationTitle.MAX_LENGTH)

        assertEquals(value, ConversationTitle(value).value)
    }

    @Test
    fun `공백만 있으면 INVALID_CONVERSATION_TITLE`() {
        val exception = assertFailsWith<BusinessException> { ConversationTitle("   ") }

        assertEquals(ErrorCode.INVALID_CONVERSATION_TITLE, exception.errorCode)
    }

    @Test
    fun `최대 길이를 넘으면 INVALID_CONVERSATION_TITLE`() {
        val tooLong = "가".repeat(ConversationTitle.MAX_LENGTH + 1)

        val exception = assertFailsWith<BusinessException> { ConversationTitle(tooLong) }

        assertEquals(ErrorCode.INVALID_CONVERSATION_TITLE, exception.errorCode)
    }
}
