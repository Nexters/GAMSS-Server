package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프롬프트 편집 이력을 담당한다. 현재값 CRUD([LlmSettingsService])와 분리된 '이력' 책임만 맡는다 —
 * 저장은 현재값 갱신과 리비전 기록을 한 트랜잭션으로 묶고, 복원은 과거 리비전 내용을
 * **새 리비전으로 저장**해 이력이 절대 끊기지 않게 한다(append-only).
 *
 * 내용이 현재값과 같은 저장·복원은 아무것도 기록하지 않는다 — 반복 클릭이 이력을 오염시키면
 * '무엇이 바뀌었는지'를 보는 감사 로그의 목적이 흐려진다.
 */
@Service
class PromptRevisionService(
    private val promptRevisionRepository: PromptRevisionRepository,
    private val llmSettingsService: LlmSettingsService,
) {
    /** 프롬프트를 저장하고 리비전을 남긴다. [savedBy]는 저장한 관리자 이메일. */
    @Transactional
    fun savePrompt(
        promptType: PromptType,
        systemPrompt: String,
        savedBy: String,
    ) {
        if (systemPrompt == llmSettingsService.currentPrompt(promptType)) {
            return
        }
        llmSettingsService.updatePrompt(promptType, systemPrompt)
        record(promptType, systemPrompt, savedBy, restoredFromVersion = null)
    }

    /**
     * 리비전 내용을 현재 프롬프트로 복원한다. 복원 자체도 새 리비전으로 기록되며
     * [PromptRevision.restoredFromVersion]에 출처 버전이 남는다.
     */
    @Transactional
    fun restore(
        revisionId: Long,
        savedBy: String,
    ): PromptRevision {
        val revision = getRevision(revisionId)
        if (revision.systemPrompt == llmSettingsService.currentPrompt(revision.promptType)) {
            return revision
        }
        llmSettingsService.updatePrompt(revision.promptType, revision.systemPrompt)
        record(revision.promptType, revision.systemPrompt, savedBy, revision.version)
        return revision
    }

    @Transactional(readOnly = true)
    fun getRevisions(
        promptType: PromptType,
        pageable: Pageable,
    ): Page<PromptRevision> = promptRevisionRepository.findAllByPromptTypeOrderByVersionDesc(promptType, pageable)

    @Transactional(readOnly = true)
    fun getRevision(id: Long): PromptRevision =
        promptRevisionRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.PROMPT_REVISION_NOT_FOUND) }

    // 최신 리비전 행을 잠가 같은 타입의 채번을 직렬화한다([PromptRevisionRepository.findLatestForUpdate]).
    private fun record(
        promptType: PromptType,
        systemPrompt: String,
        savedBy: String,
        restoredFromVersion: Int?,
    ) {
        val nextVersion = (promptRevisionRepository.findLatestForUpdate(promptType)?.version ?: 0) + 1
        promptRevisionRepository.save(PromptRevision(promptType, nextVersion, systemPrompt, savedBy, restoredFromVersion))
    }
}
