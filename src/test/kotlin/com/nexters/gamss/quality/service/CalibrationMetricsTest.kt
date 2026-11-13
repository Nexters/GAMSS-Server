package com.nexters.gamss.quality.service

import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseAiVerdict
import com.nexters.gamss.quality.domain.ResponseHumanVerdict
import com.nexters.gamss.quality.domain.ResponseVerdict
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CalibrationMetricsTest {
    @Test
    fun `AI-사람 일치율과 유형별 놓친 비율, 과잉 지적 비율, 사람끼리 일치율을 잰다`() {
        val humans =
            listOf(
                human(1, "a", ResponseVerdict.AWKWARD, AwkwardType.OFF_CONTEXT),
                human(1, "b", ResponseVerdict.AWKWARD, AwkwardType.OFF_CONTEXT),
                human(2, "a", ResponseVerdict.AWKWARD, AwkwardType.OFF_CONTEXT),
                human(3, "a", ResponseVerdict.NATURAL),
            )
        val ai =
            mapOf(
                1L to ai(1, ResponseVerdict.AWKWARD, AwkwardType.OFF_CONTEXT),
                2L to ai(2, ResponseVerdict.NATURAL),
                3L to ai(3, ResponseVerdict.AWKWARD, AwkwardType.OFF_CONTEXT),
            )

        val report = CalibrationMetrics.of(humans, ai, "judge", 1)
        val offContext = report.byType.first { it.type == AwkwardType.OFF_CONTEXT }

        assertEquals(4, report.comparedPairs)
        assertEquals(50.0, report.aiHumanAgreement)
        assertEquals(100.0, report.humanHumanAgreement)
        assertEquals(3, offContext.humanMarked)
        assertEquals(33.3, offContext.missedRate)
        assertEquals(3, offContext.aiMarked)
        assertEquals(33.3, offContext.overflagRate)
    }

    private fun human(
        messageId: Long,
        admin: String,
        verdict: ResponseVerdict,
        vararg types: AwkwardType,
    ) = ResponseHumanVerdict(messageId, 1, 1, EmotionType.JOY, ResponseKind.COMMENT, admin, verdict, types.toSet(), null)

    private fun ai(
        messageId: Long,
        verdict: ResponseVerdict,
        vararg types: AwkwardType,
    ) = ResponseAiVerdict(
        messageId = messageId,
        triggerMessageId = 100,
        conversationId = 1,
        memberId = 1,
        emotionType = EmotionType.JOY,
        responseKind = ResponseKind.COMMENT,
        messageCreatedAt = Instant.now(),
        verdict = verdict,
        awkwardTypes = types.toSet(),
        evidence = null,
        reason = null,
        judgeModel = "judge",
        judgePromptVersion = 1,
    )
}
