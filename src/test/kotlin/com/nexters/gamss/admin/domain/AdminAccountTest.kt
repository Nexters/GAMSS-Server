package com.nexters.gamss.admin.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminAccountTest {
    @Test
    fun `정규화된 이메일로 생성된다`() {
        val account = AdminAccount("admin@gamss.kr", "adder@gamss.kr")

        assertEquals("admin@gamss.kr", account.email)
    }

    @Test
    fun `이메일이 비어 있으면 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { AdminAccount(" ") }
    }

    @Test
    fun `대문자가 섞인 이메일은 정규화되지 않았으므로 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { AdminAccount("Admin@Gamss.kr") }
    }

    @Test
    fun `앞뒤 공백이 있는 이메일은 정규화되지 않았으므로 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { AdminAccount(" admin@gamss.kr ") }
    }
}
