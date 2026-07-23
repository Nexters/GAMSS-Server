package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.config.AdminProperties
import com.nexters.gamss.admin.domain.AdminAccount
import com.nexters.gamss.admin.repository.AdminAccountRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAccountServiceTest {
    private val repository = mockk<AdminAccountRepository>()
    private val adminProperties = AdminProperties(listOf("boot@gamss.kr"))
    private val service = AdminAccountService(repository, adminProperties)

    @Test
    fun `부트스트랩 이메일은 허용된다`() {
        assertTrue(service.isAllowed("BOOT@gamss.kr"))
    }

    @Test
    fun `DB에 있으면 허용된다`() {
        every { repository.existsByEmail("db@gamss.kr") } returns true

        assertTrue(service.isAllowed("db@gamss.kr"))
    }

    @Test
    fun `부트스트랩·DB 어디에도 없으면 허용되지 않는다`() {
        every { repository.existsByEmail("none@x.com") } returns false

        assertFalse(service.isAllowed("none@x.com"))
    }

    @Test
    fun `목록은 부트스트랩과 DB를 합쳐 보여준다`() {
        every { repository.findAllByOrderByCreatedAtAsc() } returns listOf(AdminAccount("db@gamss.kr", "adder@gamss.kr"))

        val result = service.list()

        assertEquals(2, result.size)
        assertEquals(AdminAccountSource.ENV, result[0].source)
        assertEquals("boot@gamss.kr", result[0].email)
        assertEquals(AdminAccountSource.DB, result[1].source)
        assertEquals("db@gamss.kr", result[1].email)
    }

    @Test
    fun `DB에도 있는 부트스트랩 이메일은 목록에서 중복되지 않는다`() {
        every { repository.findAllByOrderByCreatedAtAsc() } returns listOf(AdminAccount("boot@gamss.kr", null))

        val result = service.list()

        assertEquals(1, result.size)
        assertEquals(AdminAccountSource.DB, result[0].source)
    }

    @Test
    fun `관리자를 추가하면 정규화해 저장한다`() {
        every { repository.existsByEmail("new@x.com") } returns false
        val saved = slot<AdminAccount>()
        every { repository.saveAndFlush(capture(saved)) } answers { firstArg() }

        service.add("  New@X.com ", "Adder@Gamss.kr")

        assertEquals("new@x.com", saved.captured.email)
        assertEquals("adder@gamss.kr", saved.captured.createdByEmail)
    }

    @Test
    fun `이미 부트스트랩으로 허용된 이메일은 추가할 수 없다`() {
        val exception = assertFailsWith<BusinessException> { service.add("boot@gamss.kr", "adder@gamss.kr") }

        assertEquals(ErrorCode.ADMIN_ACCOUNT_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `이미 DB에 있는 이메일은 추가할 수 없다`() {
        every { repository.existsByEmail("db@gamss.kr") } returns true

        val exception = assertFailsWith<BusinessException> { service.add("db@gamss.kr", "adder@gamss.kr") }

        assertEquals(ErrorCode.ADMIN_ACCOUNT_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `동시 추가로 유니크 위반이 나면 ALREADY_EXISTS로 변환된다`() {
        every { repository.existsByEmail("new@x.com") } returns false
        every { repository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        val exception = assertFailsWith<BusinessException> { service.add("new@x.com", "adder@gamss.kr") }

        assertEquals(ErrorCode.ADMIN_ACCOUNT_ALREADY_EXISTS, exception.errorCode)
    }

    @Test
    fun `없는 관리자를 삭제하면 NOT_FOUND`() {
        every { repository.findById(99L) } returns java.util.Optional.empty()

        val exception = assertFailsWith<BusinessException> { service.remove(99L, "me@gamss.kr") }

        assertEquals(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `자기 자신은 삭제할 수 없다`() {
        every { repository.findById(1L) } returns java.util.Optional.of(AdminAccount("me@gamss.kr", null))

        val exception = assertFailsWith<BusinessException> { service.remove(1L, "Me@Gamss.kr") }

        assertEquals(ErrorCode.CANNOT_REMOVE_SELF, exception.errorCode)
    }

    @Test
    fun `다른 관리자는 삭제된다`() {
        val account = AdminAccount("other@gamss.kr", null)
        every { repository.findById(1L) } returns java.util.Optional.of(account)
        justRun { repository.delete(account) }

        service.remove(1L, "me@gamss.kr")

        verify(exactly = 1) { repository.delete(account) }
    }
}
