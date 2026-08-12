package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * V23 시딩이 이관 전 코드 기본값과 **바이트 단위로 동일**한지 고정한다. 골든 파일은 이관 직전
 * PromptProvider.defaultPrompt() 출력 그대로다 — 이 테스트가 깨지면 이관 과정에서 프롬프트가
 * 변형된 것이므로 시딩 SQL을 의심해야 한다.
 */
class PromptSeedIntegrationTest : RepositoryTest() {
    @Autowired
    lateinit var llmSettingsRepository: LlmSettingsRepository

    @Autowired
    lateinit var promptRevisionRepository: PromptRevisionRepository

    @Test
    fun `V23 시딩 프롬프트는 이관 전 코드 기본값과 동일하다`() {
        PromptType.entries.forEach { type ->
            val row = llmSettingsRepository.findByPromptType(type)
            assertNotNull(row, "$type 행이 시딩돼야 한다")
            assertEquals(golden(type), row.systemPrompt, "$type 프롬프트가 이관 전 기본값과 다르다")
        }
    }

    @Test
    fun `시딩된 모든 행의 model은 COMMON과 같다`() {
        // model은 COMMON 행만 읽히는 자리표시자지만, 상수로 넣으면 이후 모델 변경과 어긋난
        // 값이 남으므로 시딩이 COMMON을 참조하는지 고정한다(V23 주석 참고).
        val commonModel = checkNotNull(llmSettingsRepository.findByPromptType(PromptType.COMMON)).model
        PromptType.entries.forEach { type ->
            assertEquals(commonModel, llmSettingsRepository.findByPromptType(type)?.model, "$type 의 model이 COMMON과 다르다")
        }
    }

    @Test
    fun `시딩된 모든 타입은 v1 리비전을 가진다`() {
        PromptType.entries.forEach { type ->
            val page = promptRevisionRepository.findAllByPromptTypeOrderByVersionDesc(type, PageRequest.of(0, 50))
            assertEquals(1, page.content.count { it.version == 1 }, "$type 의 v1 리비전이 정확히 하나여야 한다")
        }
    }

    private fun golden(type: PromptType): String = checkNotNull(javaClass.getResource("/prompts/${type.name}.default.txt")).readText()
}
