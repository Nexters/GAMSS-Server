package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.service.GenerationUnit
import com.nexters.gamss.conversation.service.ResponseThreadService
import com.nexters.gamss.llm.error.ResponseJudgeFailedException
import com.nexters.gamss.llm.generation.JudgedResponse
import com.nexters.gamss.llm.generation.ResponseJudge
import com.nexters.gamss.llm.prompt.JudgeLine
import com.nexters.gamss.llm.prompt.JudgePromptContext
import com.nexters.gamss.llm.prompt.JudgeResponseLine
import com.nexters.gamss.llm.prompt.PromptCharacterId
import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.llm.settings.LlmSettingsView
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.repository.ResponseAiVerdictRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/** 생성 1회(트리거 메시지)의 응답들을 지금 채점 설정으로 채점해 저장한다. */
@Service
class QualityJudgeService(
    private val responseThreadService: ResponseThreadService,
    private val responseJudge: ResponseJudge,
    private val llmSettingsService: LlmSettingsService,
    private val memberService: MemberService,
    private val aiVerdictRepository: ResponseAiVerdictRepository,
    private val generationLogRecorder: GenerationLogRecorder,
) {
    fun currentJudge(): JudgeSnapshot {
        val view = llmSettingsService.currentJudgeView()
        return JudgeSnapshot(view, view.promptVersions.type ?: 0, llmSettingsService.currentCommonView().systemPrompt)
    }

    /** 채점할 응답이 남은 트리거만 고른다. */
    fun pendingTriggers(
        responsesByTrigger: Map<Long, List<Long>>,
        judge: JudgeSnapshot,
        scope: JudgeScope,
    ): List<Long> {
        val judged = judgedIds(responsesByTrigger.values.flatten(), judge, scope)
        return responsesByTrigger.filterValues { ids -> ids.any { it !in judged } }.keys.sorted()
    }

    fun judge(
        triggerMessageId: Long,
        judge: JudgeSnapshot,
        voteCount: Int,
        scope: JudgeScope,
    ): JudgeOutcome {
        val unit = responseThreadService.generationUnit(triggerMessageId) ?: return JudgeOutcome.SKIPPED
        if (isGone(unit.conversation.id, unit.conversation.memberId)) {
            return JudgeOutcome.SKIPPED
        }
        val judgedIds = judgedIds(unit.responses.map { it.id }, judge, scope)
        val pending = unit.responses.filter { it.id !in judgedIds }
        if (pending.isEmpty()) {
            return JudgeOutcome.ALREADY_JUDGED
        }

        val labels = unit.responses.mapIndexed { index, message -> message.id to "R${index + 1}" }.toMap()
        val context = context(unit, labels, judge.characterGuide)
        val votes = (1..voteCount).map { vote(context, judge.view) }
        val aggregated = VerdictAggregator.aggregate(unit.responses.associate { labels.getValue(it.id) to it.content }, votes)

        val verdicts =
            pending.mapNotNull { message ->
                val result = aggregated[labels.getValue(message.id)] ?: return@mapNotNull null
                ResponseAiVerdict(
                    messageId = message.id,
                    triggerMessageId = unit.trigger.id,
                    conversationId = unit.conversation.id,
                    memberId = unit.conversation.memberId,
                    emotionType = checkNotNull(message.emotionType),
                    responseKind = unit.kind,
                    messageCreatedAt = message.createdAt,
                    verdict = result.verdict,
                    awkwardTypes = result.types,
                    evidence = result.evidence,
                    reason = result.reason,
                    judgeModel = judge.view.model,
                    judgePromptVersion = judge.version,
                )
            }
        // LLM 을 기다리는 사이 삭제나 탈퇴로 판정이 지워졌다면 다시 남기지 않는다.
        if (isGone(unit.conversation.id, unit.conversation.memberId)) {
            return JudgeOutcome.SKIPPED
        }
        try {
            aiVerdictRepository.saveAll(verdicts)
        } catch (e: DataIntegrityViolationException) {
            // 다른 실행이 같은 응답을 먼저 저장했다.
            return JudgeOutcome.ALREADY_JUDGED
        }
        return JudgeOutcome.JUDGED
    }

    private fun vote(
        context: JudgePromptContext,
        view: LlmSettingsView,
    ): List<JudgedResponse> {
        val startedAt = System.currentTimeMillis()
        return try {
            responseJudge
                .judge(context, view, ResponseVerdict.entries.map { it.name }, AwkwardType.entries.map { it.name })
                .also { record(view, startedAt, true, it.usedTokens, it.cachedTokens, it.inputTokens, it.outputTokens, null) }
                .results
        } catch (e: ResponseJudgeFailedException) {
            record(view, startedAt, false, e.usedTokens, null, e.inputTokens, e.outputTokens, (e.cause ?: e).javaClass.simpleName)
            throw e
        }
    }

    private fun record(
        view: LlmSettingsView,
        startedAt: Long,
        success: Boolean,
        usedTokens: Int?,
        cachedTokens: Int?,
        inputTokens: Int?,
        outputTokens: Int?,
        failureReason: String?,
    ) {
        generationLogRecorder.record(
            type = GenerationType.QUALITY_JUDGE,
            success = success,
            attemptCount = 1,
            latencyMs = System.currentTimeMillis() - startedAt,
            usedTokens = usedTokens,
            cachedTokens = cachedTokens,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            failureReason = failureReason,
            model = view.model,
        )
    }

    private fun context(
        unit: GenerationUnit,
        labels: Map<Long, String>,
        characterGuide: String,
    ): JudgePromptContext {
        val byId = unit.responses.associateBy { it.id }
        return JudgePromptContext(
            characterGuide = characterGuide,
            prior = unit.prior.map { JudgeLine(speaker(it), it.content) },
            userMessage = JudgeLine(USER, unit.trigger.content),
            repliedTo = unit.repliedCharacterMessage?.let { JudgeLine(speaker(it), it.content) },
            responses =
                unit.responses.map { message ->
                    JudgeResponseLine(
                        label = labels.getValue(message.id),
                        speaker = speaker(message),
                        replyTo = message.repliesToMessageId?.let { byId[it] }?.let(::speaker),
                        text = message.content,
                    )
                },
            nextUserMessage = unit.nextUserMessage?.content,
        )
    }

    private fun judgedIds(
        messageIds: Collection<Long>,
        judge: JudgeSnapshot,
        scope: JudgeScope,
    ): Set<Long> {
        val verdicts =
            when (scope) {
                JudgeScope.UNJUDGED -> {
                    aiVerdictRepository.findByMessageIdIn(messageIds)
                }

                JudgeScope.CURRENT_JUDGE -> {
                    aiVerdictRepository.findByMessageIdInAndJudgeModelAndJudgePromptVersion(messageIds, judge.view.model, judge.version)
                }
            }
        return verdicts.map { it.messageId }.toSet()
    }

    private fun isGone(
        conversationId: Long,
        memberId: Long,
    ): Boolean = responseThreadService.isDeleted(conversationId) || memberService.getById(memberId).isWithdrawn()

    private fun speaker(message: Message): String =
        if (message.senderType == SenderType.USER) USER else PromptCharacterId.of(checkNotNull(message.emotionType)).promptId

    companion object {
        private const val USER = "유저"
    }
}

data class JudgeSnapshot(
    val view: LlmSettingsView,
    val version: Int,
    val characterGuide: String,
)

enum class JudgeScope {
    /** 어떤 설정으로든 판정이 없는 응답. 자동 채점 배치가 쓴다. 채점 설정을 바꿔도 지난 응답을 다시 채점하지 않는다. */
    UNJUDGED,

    /** 지금 채점 설정으로 판정이 없는 응답. 정답 세트 채점이 새 설정을 확인할 때 쓴다. */
    CURRENT_JUDGE,
}

enum class JudgeOutcome {
    JUDGED,
    ALREADY_JUDGED,
    SKIPPED,
}
