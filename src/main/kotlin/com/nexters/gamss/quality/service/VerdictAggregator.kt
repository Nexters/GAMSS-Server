package com.nexters.gamss.quality.service

import com.nexters.gamss.llm.generation.JudgedResponse
import com.nexters.gamss.quality.domain.AwkwardType
import com.nexters.gamss.quality.domain.ResponseVerdict

/** 여러 번 채점한 결과를 응답마다 하나의 판정으로 모은다. */
object VerdictAggregator {
    fun aggregate(
        responseTexts: Map<String, String>,
        votes: List<List<JudgedResponse>>,
    ): Map<String, AggregatedVerdict> =
        responseTexts
            .mapNotNull { (label, text) ->
                val ballots = votes.mapNotNull { run -> run.firstOrNull { it.label == label }?.let { normalize(it, text) } }
                if (ballots.isEmpty()) null else label to decide(ballots)
            }.toMap()

    // 근거로 든 문장이 응답에 없으면 지어낸 지적일 수 있어 애매함으로 낮춘다.
    private fun normalize(
        judged: JudgedResponse,
        text: String,
    ): AggregatedVerdict {
        val verdict = ResponseVerdict.entries.firstOrNull { it.name == judged.verdict.trim() } ?: ResponseVerdict.UNSURE
        val evidence = judged.evidence.trim()
        if (verdict == ResponseVerdict.AWKWARD && !compact(text).contains(compact(evidence).ifEmpty { NO_MATCH })) {
            return AggregatedVerdict(ResponseVerdict.UNSURE, emptySet(), evidence.ifEmpty { null }, judged.reason.trim().ifEmpty { null })
        }
        val types =
            if (verdict == ResponseVerdict.AWKWARD) {
                judged.types
                    .mapNotNull { name -> AwkwardType.entries.firstOrNull { it.name == name.trim() } }
                    .toSet()
                    .ifEmpty { setOf(AwkwardType.OTHER) }
            } else {
                emptySet()
            }
        return AggregatedVerdict(verdict, types, evidence.ifEmpty { null }, judged.reason.trim().ifEmpty { null })
    }

    private fun decide(ballots: List<AggregatedVerdict>): AggregatedVerdict {
        val counts = ballots.groupingBy { it.verdict }.eachCount()
        val top = counts.values.max()
        val leaders = counts.filterValues { it == top }.keys
        if (leaders.size > 1) {
            return AggregatedVerdict(ResponseVerdict.UNSURE, emptySet(), null, null)
        }
        val winner = leaders.single()
        val agreeing = ballots.filter { it.verdict == winner }
        val representative = agreeing.first()
        if (winner != ResponseVerdict.AWKWARD) {
            return representative
        }
        val types =
            agreeing
                .flatMap { it.types }
                .groupingBy { it }
                .eachCount()
                .filterValues { it * 2 > agreeing.size }
                .keys
                .ifEmpty { representative.types }
        return representative.copy(types = types)
    }

    private fun compact(value: String): String = value.replace(Regex("\\s+"), "")

    private const val NO_MATCH = "\u0000"
}

data class AggregatedVerdict(
    val verdict: ResponseVerdict,
    val types: Set<AwkwardType>,
    val evidence: String?,
    val reason: String?,
)
