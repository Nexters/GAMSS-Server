package com.nexters.gamss.auth.oauth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllowedAudiencesTest {
    @Test
    fun `허용 목록이 비어 있으면 모든 audience를 허용한다`() {
        val audiences = AllowedAudiences(emptyList())

        assertTrue(audiences.accepts(listOf("anything")))
        assertTrue(audiences.accepts(null))
    }

    @Test
    fun `교집합이 있으면 허용한다`() {
        val audiences = AllowedAudiences(listOf("a", "b"))

        assertTrue(audiences.accepts(listOf("b")))
    }

    @Test
    fun `교집합이 없으면 거부한다`() {
        val audiences = AllowedAudiences(listOf("a", "b"))

        assertFalse(audiences.accepts(listOf("z")))
    }

    @Test
    fun `허용 목록이 있는데 audience가 비어 있으면 거부한다`() {
        val audiences = AllowedAudiences(listOf("a"))

        assertFalse(audiences.accepts(null))
        assertFalse(audiences.accepts(emptyList()))
    }
}
