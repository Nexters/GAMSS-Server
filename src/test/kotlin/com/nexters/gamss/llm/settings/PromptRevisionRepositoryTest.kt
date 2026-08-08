package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromptRevisionRepositoryTest : RepositoryTest() {
    @Autowired
    lateinit var promptRevisionRepository: PromptRevisionRepository

    @Test
    fun `타입별 리비전을 최신 버전부터 페이지로 조회한다`() {
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, 1, "v1", null))
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, 2, "v2", "a@gamss.kr"))
        promptRevisionRepository.save(PromptRevision(PromptType.CARD, 1, "카드 v1", "a@gamss.kr"))

        val page = promptRevisionRepository.findAllByPromptTypeOrderByVersionDesc(PromptType.COMMENT, PageRequest.of(0, 10))

        assertEquals(2, page.totalElements)
        assertEquals(listOf(2, 1), page.content.map { it.version })
    }

    @Test
    fun `최대 버전을 조회하고 리비전이 없으면 null이다`() {
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, 1, "v1", null))
        promptRevisionRepository.save(PromptRevision(PromptType.COMMENT, 2, "v2", null))

        assertEquals(2, promptRevisionRepository.findMaxVersion(PromptType.COMMENT))
        assertNull(promptRevisionRepository.findMaxVersion(PromptType.REPLY))
    }
}
