package com.nexters.gamss.admin.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.service.ResponseThreadService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.service.MemberService
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseFilter
import com.nexters.gamss.quality.domain.ResponseHumanVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.service.QualityReviewService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant

/** 백오피스 대화 품질 화면의 대화 뷰어, 응답 목록, 판정하기, 실험실 입력. */
@Service
class ConversationQualityService(
    private val responseThreadService: ResponseThreadService,
    private val qualityReviewService: QualityReviewService,
    private val memberService: MemberService,
) {
    fun thread(
        conversationId: Long,
        adminEmail: String,
        blind: Boolean,
    ): ThreadView {
        val thread = responseThreadService.thread(conversationId)
        ensureReadable(thread.conversation)
        qualityReviewService.recordView(adminEmail, conversationId, thread.conversation.memberId)
        val ids = thread.responses.keys
        val ai = qualityReviewService.latestAiVerdicts(ids)
        val humans = qualityReviewService.humanVerdicts(ids)
        return ThreadView(
            conversationId = thread.conversation.id,
            memberId = thread.conversation.memberId,
            title = thread.conversation.title?.value,
            status = thread.conversation.status.name,
            summary = thread.conversation.summary,
            createdAt = thread.conversation.createdAt,
            messages =
                thread.messages.map { message ->
                    val ref = thread.responses[message.id]
                    ThreadMessage(
                        id = message.id,
                        senderType = message.senderType,
                        emotionType = message.emotionType?.name,
                        content = message.content,
                        repliesToMessageId = message.repliesToMessageId,
                        rootMessageId = message.rootMessageId,
                        createdAt = message.createdAt,
                        response =
                            ref?.let {
                                val generation = thread.generations[it.triggerMessageId]
                                val verdicts = humans[message.id].orEmpty()
                                val mine = verdicts.firstOrNull { v -> v.adminEmail == adminEmail }
                                val hidden = blind && mine == null
                                ResponseInfo(
                                    kind = it.kind,
                                    triggerMessageId = it.triggerMessageId,
                                    model = generation?.model,
                                    commonPromptVersion = generation?.commonPromptVersion,
                                    typePromptVersion = generation?.typePromptVersion,
                                    aiVerdict = if (hidden) null else ai[message.id]?.let(AiVerdictView::of),
                                    aiHidden = hidden && ai.containsKey(message.id),
                                    myVerdict = mine?.let(HumanVerdictView::of),
                                    humanVerdictCount = verdicts.size,
                                    humanAwkwardCount = verdicts.count { v -> v.verdict == ResponseVerdict.AWKWARD },
                                )
                            },
                    )
                },
        )
    }

    fun responses(
        filter: ResponseFilter,
        pageable: Pageable,
    ): Page<ResponseRow> {
        val page = qualityReviewService.findResponseIds(filter, pageable)
        if (page.content.isEmpty()) {
            return PageImpl(emptyList(), pageable, page.totalElements)
        }
        val details = responseThreadService.responses(page.content).associateBy { it.ref.message.id }
        val ai = qualityReviewService.latestAiVerdicts(page.content)
        val humans = qualityReviewService.humanVerdicts(page.content)
        val rows =
            page.content.mapNotNull { id ->
                val detail = details[id] ?: return@mapNotNull null
                val message = detail.ref.message
                val verdicts = humans[id].orEmpty()
                ResponseRow(
                    messageId = id,
                    conversationId = message.conversationId,
                    emotionType = checkNotNull(message.emotionType).name,
                    kind = detail.ref.kind,
                    content = message.content,
                    createdAt = message.createdAt,
                    model = detail.generation?.model,
                    typePromptVersion = detail.generation?.typePromptVersion,
                    aiVerdict = ai[id]?.let(AiVerdictView::of),
                    myVerdict = verdicts.firstOrNull { it.adminEmail == filter.adminEmail }?.let(HumanVerdictView::of),
                    humanVerdictCount = verdicts.size,
                )
            }
        return PageImpl(rows, pageable, page.totalElements)
    }

    fun nextLabeling(
        adminEmail: String,
        exclude: Collection<Long>,
    ): LabelingNext {
        val messageId = qualityReviewService.nextLabelingTarget(adminEmail, exclude)
        val conversationId =
            messageId?.let {
                responseThreadService
                    .responses(listOf(it))
                    .firstOrNull()
                    ?.conversation
                    ?.id
            }
        return LabelingNext(messageId, conversationId, qualityReviewService.countMine(adminEmail), LABELING_TARGET)
    }

    /** 사람 판정을 저장하고, 가려 두었던 AI 판정을 함께 돌려준다. */
    fun saveHumanVerdict(
        adminEmail: String,
        messageId: Long,
        verdict: ResponseVerdict,
        awkwardTypes: Set<AwkwardType>,
        memo: String?,
    ): HumanVerdictResult {
        val response =
            responseThreadService.responses(listOf(messageId)).firstOrNull() ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        ensureReadable(response.conversation)
        val saved = qualityReviewService.saveHumanVerdict(adminEmail, messageId, verdict, awkwardTypes, memo)
        val ai = qualityReviewService.latestAiVerdicts(listOf(messageId))[messageId]
        return HumanVerdictResult(
            messageId = messageId,
            myVerdict = HumanVerdictView.of(saved),
            aiVerdict = ai?.let(AiVerdictView::of),
            agreesWithAi = ai?.let { it.verdict == saved.verdict },
            mineCount = qualityReviewService.countMine(adminEmail),
        )
    }

    fun playgroundInput(messageId: Long): PlaygroundInput {
        val detail =
            responseThreadService.responses(listOf(messageId)).firstOrNull() ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        ensureReadable(detail.conversation)
        val unit = responseThreadService.generationUnit(detail.ref.triggerMessageId) ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        return when (unit.kind) {
            ResponseKind.COMMENT -> {
                val firstRound = unit.responses.filter { it.repliesToMessageId == null }
                PlaygroundInput(
                    kind = ResponseKind.COMMENT,
                    diary = unit.trigger.content,
                    summary = unit.conversation.summary,
                    characters = firstRound.mapNotNull { it.emotionType?.name }.distinct(),
                    tikitakaCount = unit.responses.size - firstRound.size,
                )
            }

            ResponseKind.REPLY -> {
                PlaygroundInput(
                    kind = ResponseKind.REPLY,
                    diary = unit.diary?.content.orEmpty(),
                    summary = unit.conversation.summary,
                    characters =
                        listOfNotNull(
                            detail.ref.message.emotionType
                                ?.name,
                        ),
                    tikitakaCount = 0,
                    character =
                        detail.ref.message.emotionType
                            ?.name,
                    characterComment = unit.repliedCharacterMessage?.content,
                    userReply = unit.trigger.content,
                )
            }
        }
    }

    // 삭제나 탈퇴로 판정을 지운 대화는 원문을 열거나 새로 판정하지 않는다.
    private fun ensureReadable(conversation: Conversation) {
        conversation.ensureNotDeleted()
        if (memberService.getById(conversation.memberId).isWithdrawn()) {
            throw BusinessException(ErrorCode.WITHDRAWN_MEMBER)
        }
    }

    companion object {
        const val LABELING_TARGET = 100
    }
}

data class ThreadView(
    val conversationId: Long,
    val memberId: Long,
    val title: String?,
    val status: String,
    val summary: String?,
    val createdAt: Instant,
    val messages: List<ThreadMessage>,
)

data class ThreadMessage(
    val id: Long,
    val senderType: SenderType,
    val emotionType: String?,
    val content: String,
    val repliesToMessageId: Long?,
    val rootMessageId: Long?,
    val createdAt: Instant,
    val response: ResponseInfo?,
)

data class ResponseInfo(
    val kind: ResponseKind,
    val triggerMessageId: Long,
    val model: String?,
    val commonPromptVersion: Int?,
    val typePromptVersion: Int?,
    val aiVerdict: AiVerdictView?,
    val aiHidden: Boolean,
    val myVerdict: HumanVerdictView?,
    val humanVerdictCount: Int,
    val humanAwkwardCount: Int,
)

data class AiVerdictView(
    val verdict: ResponseVerdict,
    val awkwardTypes: List<AwkwardType>,
    val evidence: String?,
    val reason: String?,
    val judgeModel: String,
    val judgePromptVersion: Int,
    val createdAt: Instant,
) {
    companion object {
        fun of(v: ResponseAiVerdict) =
            AiVerdictView(v.verdict, v.awkwardTypes.toList(), v.evidence, v.reason, v.judgeModel, v.judgePromptVersion, v.createdAt)
    }
}

data class HumanVerdictView(
    val verdict: ResponseVerdict,
    val awkwardTypes: List<AwkwardType>,
    val memo: String?,
    val updatedAt: Instant,
) {
    companion object {
        fun of(v: ResponseHumanVerdict) = HumanVerdictView(v.verdict, v.awkwardTypes.toList(), v.memo, v.updatedAt)
    }
}

data class ResponseRow(
    val messageId: Long,
    val conversationId: Long,
    val emotionType: String,
    val kind: ResponseKind,
    val content: String,
    val createdAt: Instant,
    val model: String?,
    val typePromptVersion: Int?,
    val aiVerdict: AiVerdictView?,
    val myVerdict: HumanVerdictView?,
    val humanVerdictCount: Int,
)

data class LabelingNext(
    val messageId: Long?,
    val conversationId: Long?,
    val mineCount: Long,
    val target: Int,
)

data class HumanVerdictResult(
    val messageId: Long,
    val myVerdict: HumanVerdictView,
    val aiVerdict: AiVerdictView?,
    val agreesWithAi: Boolean?,
    val mineCount: Long,
)

data class PlaygroundInput(
    val kind: ResponseKind,
    val diary: String,
    val summary: String?,
    val characters: List<String>,
    val tikitakaCount: Int,
    val character: String? = null,
    val characterComment: String? = null,
    val userReply: String? = null,
)
