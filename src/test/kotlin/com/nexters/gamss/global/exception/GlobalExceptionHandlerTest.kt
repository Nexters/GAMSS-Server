package com.nexters.gamss.global.exception

import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `비즈니스 예외를 에러 코드에 맞는 응답으로 변환한다`() {
        val response = handler.handleBusiness(BusinessException(ErrorCode.MEMBER_NOT_FOUND))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertFalse(body.success)
        assertNull(body.data)
        assertEquals("MEMBER_NOT_FOUND", body.error!!.code)
        assertEquals(ErrorCode.MEMBER_NOT_FOUND.message, body.error!!.message)
    }

    @Test
    fun `비즈니스 예외의 커스텀 메시지를 그대로 전달한다`() {
        val response = handler.handleBusiness(BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN, "서명 검증 실패"))

        assertEquals("서명 검증 실패", response.body!!.error!!.message)
    }

    @Test
    fun `검증 실패는 400 INVALID_INPUT 으로 변환한다`() {
        val exception = mockk<MethodArgumentNotValidException>()
        val bindingResult = mockk<BindingResult>()
        every { exception.bindingResult } returns bindingResult
        every { bindingResult.fieldErrors } returns emptyList()

        val response = handler.handleValidation(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_INPUT", response.body!!.error!!.code)
    }

    @Test
    fun `본문 파싱 실패는 400 INVALID_INPUT 으로 변환한다`() {
        val response = handler.handleNotReadable(mockk<HttpMessageNotReadableException>())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_INPUT", response.body!!.error!!.code)
    }

    @Test
    fun `예상치 못한 예외는 500 INTERNAL_ERROR 로 변환한다`() {
        val response = handler.handleUnexpected(RuntimeException("boom"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body!!.error!!.code)
    }
}
