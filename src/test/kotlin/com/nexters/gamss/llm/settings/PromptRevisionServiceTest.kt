package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.prompt.PromptType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PromptRevisionServiceTest {
    private val promptRevisionRepository = mockk<PromptRevisionRepository>()
    private val llmSettingsService = mockk<LlmSettingsService>()
    private val service = PromptRevisionService(promptRevisionRepository, llmSettingsService)

    @Test
    fun `저장하면 현재값을 갱신하고 다음 버전 리비전을 남긴다`() {
        every { llmSettingsService.currentPrompt(PromptType.COMMENT) } returns "이전 프롬프트"
        every { llmSettingsService.updatePrompt(PromptType.COMMENT, "새 프롬프트") } returns Unit
        every { promptRevisionRepository.findLatestForUpdate(PromptType.COMMENT) } returns
            PromptRevision(PromptType.COMMENT, 3, "이전 프롬프트", null)
        val saved = slot<PromptRevision>()
        every { promptRevisionRepository.save(capture(saved)) } answers { firstArg() }

        service.savePrompt(PromptType.COMMENT, "새 프롬프트", "admin@gamss.kr")

        verify(exactly = 1) { llmSettingsService.updatePrompt(PromptType.COMMENT, "새 프롬프트") }
        assertEquals(4, saved.captured.version)
        assertEquals("새 프롬프트", saved.captured.systemPrompt)
        assertEquals("admin@gamss.kr", saved.captured.savedBy)
        assertNull(saved.captured.restoredFromVersion)
    }

    @Test
    fun `리비전이 하나도 없으면 버전 1로 기록한다`() {
        every { llmSettingsService.currentPrompt(PromptType.CARD) } returns "이전"
        every { llmSettingsService.updatePrompt(PromptType.CARD, "새 값") } returns Unit
        every { promptRevisionRepository.findLatestForUpdate(PromptType.CARD) } returns null
        val saved = slot<PromptRevision>()
        every { promptRevisionRepository.save(capture(saved)) } answers { firstArg() }

        service.savePrompt(PromptType.CARD, "새 값", "admin@gamss.kr")

        assertEquals(1, saved.captured.version)
    }

    @Test
    fun `내용이 현재값과 같으면 갱신도 리비전도 남기지 않는다`() {
        every { llmSettingsService.currentPrompt(PromptType.COMMENT) } returns "같은 프롬프트"

        service.savePrompt(PromptType.COMMENT, "같은 프롬프트", "admin@gamss.kr")

        verify(exactly = 0) { llmSettingsService.updatePrompt(any(), any()) }
        verify(exactly = 0) { promptRevisionRepository.save(any()) }
    }

    @Test
    fun `복원하면 리비전 내용으로 갱신하고 출처 버전을 남긴 새 리비전을 기록한다`() {
        val target = PromptRevision(PromptType.COMMENT, 2, "v2 프롬프트", "old@gamss.kr")
        every { promptRevisionRepository.findById(10L) } returns Optional.of(target)
        every { llmSettingsService.currentPrompt(PromptType.COMMENT) } returns "현재 프롬프트"
        every { llmSettingsService.updatePrompt(PromptType.COMMENT, "v2 프롬프트") } returns Unit
        every { promptRevisionRepository.findLatestForUpdate(PromptType.COMMENT) } returns
            PromptRevision(PromptType.COMMENT, 5, "현재 프롬프트", null)
        val saved = slot<PromptRevision>()
        every { promptRevisionRepository.save(capture(saved)) } answers { firstArg() }

        service.restore(10L, "admin@gamss.kr")

        verify(exactly = 1) { llmSettingsService.updatePrompt(PromptType.COMMENT, "v2 프롬프트") }
        assertEquals(6, saved.captured.version)
        assertEquals("v2 프롬프트", saved.captured.systemPrompt)
        assertEquals("admin@gamss.kr", saved.captured.savedBy)
        assertEquals(2, saved.captured.restoredFromVersion)
    }

    @Test
    fun `현재값과 같은 내용의 리비전을 복원하면 아무것도 기록하지 않는다`() {
        val target = PromptRevision(PromptType.COMMENT, 2, "현재 프롬프트", "old@gamss.kr")
        every { promptRevisionRepository.findById(10L) } returns Optional.of(target)
        every { llmSettingsService.currentPrompt(PromptType.COMMENT) } returns "현재 프롬프트"

        service.restore(10L, "admin@gamss.kr")

        verify(exactly = 0) { llmSettingsService.updatePrompt(any(), any()) }
        verify(exactly = 0) { promptRevisionRepository.save(any()) }
    }

    @Test
    fun `없는 리비전을 복원하면 PROMPT_REVISION_NOT_FOUND`() {
        every { promptRevisionRepository.findById(99L) } returns Optional.empty()

        val exception = assertFailsWith<BusinessException> { service.restore(99L, "admin@gamss.kr") }

        assertEquals(ErrorCode.PROMPT_REVISION_NOT_FOUND, exception.errorCode)
    }
}
