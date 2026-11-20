package com.nexters.gamss.quality.service

import com.nexters.gamss.llm.generation.JudgedResponse
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerdictAggregatorTest {
    private val texts = mapOf("R1" to "혹시 내일 비 와?", "R2" to "힘들었겠다")

    @Test
    fun `다수결로 판정하고 다수 쪽의 근거를 쓴다`() {
        val result =
            VerdictAggregator.aggregate(
                texts,
                listOf(
                    listOf(awkward("R1", "내일 비 와", AwkwardType.OFF_CONTEXT), natural("R2")),
                    listOf(awkward("R1", "비 와?", AwkwardType.OFF_CONTEXT, AwkwardType.OTHER), natural("R2")),
                    listOf(natural("R1"), natural("R2")),
                ),
            )

        assertEquals(ResponseVerdict.AWKWARD, result.getValue("R1").verdict)
        assertEquals(setOf(AwkwardType.OFF_CONTEXT), result.getValue("R1").types)
        assertEquals("내일 비 와", result.getValue("R1").evidence)
        assertEquals(ResponseVerdict.NATURAL, result.getValue("R2").verdict)
    }

    @Test
    fun `셋이 모두 다르면 애매함이다`() {
        val result =
            VerdictAggregator.aggregate(
                texts,
                listOf(
                    listOf(awkward("R1", "비 와", AwkwardType.OFF_CONTEXT)),
                    listOf(natural("R1")),
                    listOf(JudgedResponse("R1", "", "", "UNSURE", emptyList())),
                ),
            )

        assertEquals(ResponseVerdict.UNSURE, result.getValue("R1").verdict)
    }

    @Test
    fun `근거가 응답에 없으면 어색함을 애매함으로 낮춘다`() {
        val result = VerdictAggregator.aggregate(texts, listOf(listOf(awkward("R2", "응답에 없는 문장", AwkwardType.REPETITIVE))))

        assertEquals(ResponseVerdict.UNSURE, result.getValue("R2").verdict)
    }

    @Test
    fun `유형 없이 어색함이면 기타로 둔다`() {
        val result = VerdictAggregator.aggregate(texts, listOf(listOf(JudgedResponse("R2", "힘들었겠다", "이유", "AWKWARD", emptyList()))))

        assertEquals(setOf(AwkwardType.OTHER), result.getValue("R2").types)
    }

    @Test
    fun `결과가 없는 라벨은 판정하지 않는다`() {
        val result = VerdictAggregator.aggregate(texts, listOf(listOf(natural("R1"))))

        assertNull(result["R2"])
    }

    private fun awkward(
        label: String,
        evidence: String,
        vararg types: AwkwardType,
    ) = JudgedResponse(label, evidence, "이유", "AWKWARD", types.map { it.name })

    private fun natural(label: String) = JudgedResponse(label, "", "", "NATURAL", emptyList())
}
