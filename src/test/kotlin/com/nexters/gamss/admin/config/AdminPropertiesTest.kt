package com.nexters.gamss.admin.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPropertiesTest {
    @Test
    fun `허용목록에 있는 이메일은 대소문자와 공백을 무시하고 허용한다`() {
        val properties = AdminProperties(listOf(" Admin@Gamss.KR "))

        assertTrue(properties.isAllowed("admin@gamss.kr"))
        assertTrue(properties.isAllowed("ADMIN@GAMSS.KR"))
    }

    @Test
    fun `허용목록에 없는 이메일은 거부한다`() {
        val properties = AdminProperties(listOf("admin@gamss.kr"))

        assertFalse(properties.isAllowed("someone@else.com"))
    }

    @Test
    fun `허용목록이 비어 있으면 누구도 허용하지 않는다`() {
        val properties = AdminProperties(emptyList())

        assertFalse(properties.isAllowed("admin@gamss.kr"))
    }
}
