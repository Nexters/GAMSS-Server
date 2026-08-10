package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.prompt.PromptType
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프롬프트 편집 이력을 담당한다. 현재값 CRUD([LlmSettingsService])와 분리된 '이력' 책임만 맡는다 —
 * 저장은 현재값 갱신과 리비전 기록을 한 트랜잭션으로 묶고, 복원은 과거 리비전 내용을
 * **새 리비전으로 저장**해 이력이 절대 끊기지 않게 한다(append-only).
 *
 * 동시 저장의 채번(max+1) 경합은 설정 행 잠금([LlmSettingsRepository.findByPromptTypeForUpdate])으로
 * 직렬화한다. 잠글 행이 아직 없는 최초 기록만 경합이 열리는데, 이때의 유니크 제약 위반은
 * [PromptRevisionConflictException]으로 번역해 트랜잭션 바깥의 재시도가 해소한다.
 *
 * 내용이 현재값과 같은 저장·복원은 아무것도 기록하지 않는다 — 반복 클릭이 이력을 오염시키면
 * '무엇이 바뀌었는지'를 보는 감사 로그의 목적이 흐려진다. 비교·기록 전에 앞뒤 공백을 다듬어
 * 프런트의 dirty 판단(trim 기준)과 어긋나거나 공백만 다른 리비전이 쌓이는 일을 막는다.
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
        val content = systemPrompt.trim()
        val current = lockedCurrentPrompt(promptType)
        if (content == current) {
            return
        }
        mutate(promptType, content, savedBy, restoredFromVersion = null)
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
        // 과거(공백 다듬기 도입 전) 리비전을 복원해도 공백이 되살아나지 않게 여기서도 다듬는다.
        val content = revision.systemPrompt.trim()
        val current = lockedCurrentPrompt(revision.promptType)
        if (content == current) {
            return revision
        }
        mutate(revision.promptType, content, savedBy, revision.version)
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

    /**
     * 설정 행을 잠가 같은 타입의 채번을 직렬화한 뒤 현재값을 읽는다.
     * 행이 없으면(최초 기록) 잠금 없이 코드 기본값과 비교한다 — 이 좁은 창구의 경합은
     * 유니크 제약 + 재시도가 막는다. 과거에 공백이 섞여 저장된 값과도 같은 기준으로 비교되게
     * 현재값 역시 다듬어 돌려준다.
     */
    private fun lockedCurrentPrompt(promptType: PromptType): String =
        (llmSettingsService.currentPromptForUpdate(promptType) ?: llmSettingsService.currentPrompt(promptType)).trim()

    // 현재값 갱신과 리비전 기록을 함께 커밋한다. 즉시 flush해 유니크 위반이 이 안에서 잡히게 한다
    // (커밋 시점으로 미루면 번역할 기회가 없다). 최초 기록 경합은 유니크 위반뿐 아니라
    // 갭 락 데드락(ConcurrencyFailureException)으로도 나타나므로 둘 다 회복 가능한 충돌로 번역한다.
    private fun mutate(
        promptType: PromptType,
        systemPrompt: String,
        savedBy: String,
        restoredFromVersion: Int?,
    ) {
        try {
            llmSettingsService.updatePrompt(promptType, systemPrompt)
            val nextVersion = (promptRevisionRepository.findMaxVersion(promptType) ?: 0) + 1
            promptRevisionRepository.saveAndFlush(PromptRevision(promptType, nextVersion, systemPrompt, savedBy, restoredFromVersion))
        } catch (e: DataIntegrityViolationException) {
            throw PromptRevisionConflictException(promptType, e)
        } catch (e: ConcurrencyFailureException) {
            throw PromptRevisionConflictException(promptType, e)
        }
    }
}
