package com.nexters.gamss.llm.settings

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 마이그레이션을 다 적용한 뒤의 프롬프트가 골든 파일과 **바이트 단위로 동일**한지 고정한다.
 * 골든 파일의 출발점은 V23 이관 직전 PromptProvider.defaultPrompt() 출력이고, 이후 프롬프트를
 * 바꾸는 마이그레이션(V26 등)이 생기면 골든 파일도 함께 갱신한다 — 이 테스트가 깨지면
 * 마이그레이션이 의도와 다른 문자열을 넣은 것이므로 해당 SQL을 의심해야 한다.
 */
class PromptSeedIntegrationTest : RepositoryTest() {
    @Autowired
    lateinit var llmSettingsRepository: LlmSettingsRepository

    @Autowired
    lateinit var promptRevisionRepository: PromptRevisionRepository

    @Test
    fun `마이그레이션이 적용된 프롬프트는 골든 파일과 동일하다`() {
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

    @Test
    fun `모든 타입의 최신 리비전은 현재 프롬프트와 같다`() {
        // 프롬프트를 바꾸는 마이그레이션은 현재값 UPDATE와 리비전 append를 함께 해야 한다(V26·V29).
        // 리비전을 빠뜨려도 골든 비교는 통과하므로, 백오피스 이력이 끊기는 것은 여기서만 드러난다.
        PromptType.entries.forEach { type ->
            val latest =
                promptRevisionRepository
                    .findAllByPromptTypeOrderByVersionDesc(type, PageRequest.of(0, 1))
                    .content
                    .firstOrNull()
            assertNotNull(latest, "$type 의 리비전이 있어야 한다")
            assertEquals(
                checkNotNull(llmSettingsRepository.findByPromptType(type)).systemPrompt,
                latest.systemPrompt,
                "$type 의 최신 리비전이 현재 프롬프트와 다르다 — 마이그레이션이 리비전 append를 빠뜨렸다",
            )
        }
    }

    private fun golden(type: PromptType): String = checkNotNull(javaClass.getResource("/prompts/${type.name}.default.txt")).readText()
}
