package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.ResponseJudge
import com.nexters.gamss.llm.settings.LlmSettingsService
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.repository.GenerationLogRepository
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseVerdict
import com.nexters.gamss.quality.repository.ResponseAiVerdictRepository
import com.nexters.gamss.support.FakeResponseJudge
import com.nexters.gamss.support.FakeResponseJudgeConfig
import com.nexters.gamss.support.RepositoryTest
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(FakeResponseJudgeConfig::class)
class QualityJudgeIntegrationTest : RepositoryTest() {
    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var conversationRepository: ConversationRepository

    @Autowired
    lateinit var messageRepository: MessageRepository

    @Autowired
    lateinit var conversationService: ConversationService

    @Autowired
    lateinit var qualityJudgeService: QualityJudgeService

    @Autowired
    lateinit var qualityJudgeRunner: QualityJudgeRunner

    @Autowired
    lateinit var qualityJudgeScheduler: QualityJudgeScheduler

    @Autowired
    lateinit var qualityJudgeSettingService: QualityJudgeSettingService

    @Autowired
    lateinit var aiVerdictRepository: ResponseAiVerdictRepository

    @Autowired
    lateinit var generationLogRepository: GenerationLogRepository

    @Autowired
    lateinit var llmSettingsService: LlmSettingsService

    @Autowired
    lateinit var responseJudge: ResponseJudge

    private val judge get() = responseJudge as FakeResponseJudge

    @BeforeEach
    fun reset() {
        judge.calls = 0
        judge.duringCall = {}
        judge.contexts.clear()
    }

    @Test
    fun `생성 1회의 응답들을 채점 횟수만큼 불러 다수결로 저장하고 다시 돌려도 쌓지 않는다`() {
        val (member, diary, comments) = seedDiary()

        val judgeSnapshot = qualityJudgeService.currentJudge()
        assertEquals(JudgeOutcome.JUDGED, qualityJudgeService.judge(diary.id, judgeSnapshot, 3))
        assertEquals(JudgeOutcome.ALREADY_JUDGED, qualityJudgeService.judge(diary.id, judgeSnapshot, 3))

        assertEquals(3, judge.calls)
        val verdicts = aiVerdictRepository.findByMessageIdIn(comments.map { it.id }).associateBy { it.messageId }
        assertEquals(ResponseVerdict.NATURAL, verdicts.getValue(comments[0].id).verdict)
        assertEquals(ResponseVerdict.AWKWARD, verdicts.getValue(comments[1].id).verdict)
        assertEquals(setOf(AwkwardType.OFF_CONTEXT), verdicts.getValue(comments[1].id).awkwardTypes)
        assertEquals(ResponseVerdict.NATURAL, verdicts.getValue(comments[2].id).verdict)
        assertEquals(ResponseKind.COMMENT, verdicts.getValue(comments[2].id).responseKind)
        assertEquals(member.id, verdicts.getValue(comments[0].id).memberId)
        assertEquals(
            "gippeum",
            judge.contexts
                .first()
                .responses
                .first { it.speaker == "kkachil" }
                .replyTo,
        )

        val logs = generationLogRepository.findByGenerationTypeAndCreatedAtGreaterThanEqual(GenerationType.QUALITY_JUDGE, Instant.EPOCH)
        assertEquals(3, logs.size)
        assertTrue(logs.all { it.memberId == null && it.model == llmSettingsService.currentJudgeView().model })
    }

    @Test
    fun `답글 재응답은 답장한 유저 메시지 단위로 채점하고 다음 유저 메시지를 함께 준다`() {
        val (_, diary, comments) = seedDiary()
        val reply = save(diary.conversationId, SenderType.USER, null, "고마워", repliesTo = comments[0].id)
        val response = save(diary.conversationId, SenderType.CHARACTER, EmotionType.JOY, "응응", repliesTo = reply.id, root = diary.id)
        save(diary.conversationId, SenderType.USER, null, "뭔소리야")

        qualityJudgeService.judge(reply.id, qualityJudgeService.currentJudge(), 1)

        val verdict = aiVerdictRepository.findByMessageIdIn(listOf(response.id)).single()
        assertEquals(ResponseKind.REPLY, verdict.responseKind)
        assertEquals("뭔소리야", judge.contexts.single().nextUserMessage)
        assertEquals(
            comments[0].content,
            judge.contexts
                .single()
                .repliedTo
                ?.text,
        )
        assertEquals(
            1,
            judge.contexts
                .single()
                .responses.size,
        )
    }

    @Test
    fun `자동 채점이 꺼져 있으면 배치가 아무것도 하지 않고, 켜면 지난 하루의 응답을 채점한다`() {
        val (_, _, comments) = seedDiary()
        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1).withHour(6)

        qualityJudgeSettingService.update(autoEnabled = false, voteCount = 1)
        qualityJudgeScheduler.judgeRecentResponses()
        assertEquals(0, judge.calls)

        qualityJudgeSettingService.update(autoEnabled = true, voteCount = 1)
        val tally = qualityJudgeScheduler.runFor(now)

        assertEquals(1, tally?.judged)
        assertEquals(3, aiVerdictRepository.findByMessageIdIn(comments.map { it.id }).size)
    }

    @Test
    fun `대화를 삭제하면 판정도 지운다`() {
        val (member, diary, comments) = seedDiary()
        qualityJudgeService.judge(diary.id, qualityJudgeService.currentJudge(), 1)

        conversationService.deleteConversation(member.id, diary.conversationId)

        assertTrue(aiVerdictRepository.findByMessageIdIn(comments.map { it.id }).isEmpty())
    }

    @Test
    fun `채점하는 사이 대화가 삭제되면 판정을 남기지 않는다`() {
        val (member, diary, comments) = seedDiary()
        judge.duringCall = { conversationService.deleteConversation(member.id, diary.conversationId) }

        val outcome = qualityJudgeService.judge(diary.id, qualityJudgeService.currentJudge(), 1)

        assertEquals(JudgeOutcome.SKIPPED, outcome)
        assertTrue(aiVerdictRepository.findByMessageIdIn(comments.map { it.id }).isEmpty())
    }

    @Test
    fun `정답 세트 채점 대상은 판정이 남은 트리거만이다`() {
        val (_, diary, comments) = seedDiary()
        val judgeSnapshot = qualityJudgeService.currentJudge()

        assertEquals(listOf(diary.id), qualityJudgeRunner.pendingTriggersOfMessages(comments.map { it.id }, judgeSnapshot))
        qualityJudgeService.judge(diary.id, judgeSnapshot, 1)
        assertTrue(qualityJudgeRunner.pendingTriggersOfMessages(comments.map { it.id }, judgeSnapshot).isEmpty())
    }

    private fun seedDiary(): Triple<Member, Message, List<Message>> {
        val member = memberRepository.save(Member("me@a.com"))
        val conversation = conversationRepository.save(Conversation(member.id))
        val diary = save(conversation.id, SenderType.USER, null, "오늘 팀장한테 깨졌어")
        val joy = save(conversation.id, SenderType.CHARACTER, EmotionType.JOY, "괜찮아 잘했어", root = diary.id)
        val anxiety = save(conversation.id, SenderType.CHARACTER, EmotionType.ANXIETY, "혹시 내일 날씨 어때?", root = diary.id)
        val grumpy = save(conversation.id, SenderType.CHARACTER, EmotionType.GRUMPY, "뭘 잘해", repliesTo = joy.id, root = diary.id)
        return Triple(member, diary, listOf(joy, anxiety, grumpy))
    }

    private fun save(
        conversationId: Long,
        senderType: SenderType,
        emotionType: EmotionType?,
        content: String,
        repliesTo: Long? = null,
        root: Long? = null,
    ): Message =
        messageRepository.save(
            Message(
                conversationId = conversationId,
                senderType = senderType,
                emotionType = emotionType,
                content = content,
                repliesToMessageId = repliesTo,
                rootMessageId = root,
            ),
        )
}
