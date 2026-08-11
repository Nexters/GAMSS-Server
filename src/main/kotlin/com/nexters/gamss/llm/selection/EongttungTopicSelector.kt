package com.nexters.gamss.llm.selection

import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.llm.settings.LlmSettingsService
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 엉뚱이가 다룰 "소재"를 서버가 미리 골라서 프롬프트에 박아 넣는다. LLM에게 "아무 얘기나 뜬금없이
 * 해봐"라고 맡기면 [CharacterSelector]에서 이미 확인한 것과 같은 문제(LLM의 "무작위"는 통계적으로
 * 균등하지 않고 특정 소재로 수렴하기 쉬움)가 그대로 반복된다 — 이 프롬프트 자체도 소재를 "user 메시지에
 * 별도로 주어진다"고 전제하고 있어 LLM 즉흥 생성을 상정하지 않는다.
 *
 * 소재 목록의 단일 원본은 DB([PromptType.EONGTTUNG_TOPIC], 한 줄에 소재 하나)다 — 백오피스에서
 * 편집하고 버전 이력이 남는다(V24 이관).
 */
@Component
class EongttungTopicSelector(
    private val llmSettingsService: LlmSettingsService,
    private val random: Random = Random.Default,
) {
    fun select(): String {
        val topics =
            llmSettingsService
                .currentPrompt(PromptType.EONGTTUNG_TOPIC)
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        check(topics.isNotEmpty()) { "엉뚱이 소재가 비어 있습니다. 백오피스에서 EONGTTUNG_TOPIC 프롬프트를 확인하세요." }
        return topics.random(random)
    }
}
