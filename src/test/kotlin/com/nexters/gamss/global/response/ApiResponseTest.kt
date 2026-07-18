package com.nexters.gamss.global.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiResponseTest {
    @Test
    fun `데이터를 담은 성공 응답을 생성한다`() {
        val response = ApiResponse.success("hello")

        assertTrue(response.success)
        assertEquals("hello", response.data)
        assertNull(response.error)
    }

    @Test
    fun `데이터 없는 성공 응답을 생성한다`() {
        val response = ApiResponse.success()

        assertTrue(response.success)
        assertNull(response.error)
    }

    @Test
    fun `에러 응답을 생성한다`() {
        val response = ApiResponse.error(ErrorResponse("INVALID_TOKEN", "유효하지 않은 토큰입니다."))

        assertFalse(response.success)
        assertNull(response.data)
        assertEquals("INVALID_TOKEN", response.error!!.code)
    }
}
