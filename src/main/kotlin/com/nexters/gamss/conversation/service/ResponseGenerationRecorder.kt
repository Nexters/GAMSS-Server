package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.ResponseGeneration
import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.conversation.repository.ResponseGenerationRepository
import com.nexters.gamss.llm.settings.PromptVersions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 품질 분석용 부가 기록이라 실패해도 이미 저장된 응답을 실패로 돌리지 않는다. */
@Component
class ResponseGenerationRecorder(
    private val responseGenerationRepository: ResponseGenerationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(
        triggerMessageId: Long,
        conversationId: Long,
        kind: ResponseKind,
        model: String?,
        promptVersions: PromptVersions,
    ) {
        runCatching {
            if (responseGenerationRepository.existsByTriggerMessageId(triggerMessageId)) {
                return
            }
            responseGenerationRepository.save(
                ResponseGeneration(
                    triggerMessageId = triggerMessageId,
                    conversationId = conversationId,
                    kind = kind,
                    model = model,
                    commonPromptVersion = promptVersions.common,
                    typePromptVersion = promptVersions.type,
                ),
            )
        }.onFailure { log.warn("응답 생성 기록 실패(무시) triggerMessageId={}", triggerMessageId, it) }
    }
}
