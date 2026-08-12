package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals

// 시딩(V22 이후) 여부와 무관하게 동작하도록, 테스트 리비전은 항상 현재 최신 버전 위에 쌓는다.
class PromptRevisionRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var promptRevisionRepository: PromptRevisionRepository

    @Test
    fun `타입별 리비전을 최신 버전부터 페이지로 조회한다`() {
        val base = latestVersion(PromptType.COMMENT)
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, base + 1, "새 버전", "a@gamss.kr"))
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, base + 2, "더 새 버전", "a@gamss.kr"))

        val page = promptRevisionRepository.findAllByPromptTypeOrderByVersionDesc(PromptType.COMMENT, PageRequest.of(0, 2))

        assertEquals(listOf(base + 2, base + 1), page.content.map { it.version })
    }

    @Test
    fun `최대 버전을 조회한다 - 다음 버전 채번의 기준`() {
        val base = latestVersion(PromptType.CARD)
        promptRevisionRepository.save(PromptRevision(PromptType.CARD, base + 1, "새 버전", null))

        assertEquals(base + 1, promptRevisionRepository.findMaxVersion(PromptType.CARD))
    }

    private fun latestVersion(promptType: PromptType): Int = promptRevisionRepository.findMaxVersion(promptType) ?: 0
}
