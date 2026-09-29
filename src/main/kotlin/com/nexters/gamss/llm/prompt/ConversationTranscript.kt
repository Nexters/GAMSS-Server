package com.nexters.gamss.llm.prompt

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 이번 응답 직전까지 이 방에서 오간 말 중 LLM에 실을 구간. 무엇을 담을지의 규칙을 이 한 곳이 소유한다.
 *
 * 프론트가 보내는 요약([CommentPromptContext.currentConversationSummary])은 압축본이라 "그게 무슨 소리야"의
 * "그게"처럼 직전 발언을 가리키는 말을 풀 수 없다. 그래서 최근 원문을 따로 싣는다. 요약은 오래된 맥락,
 * 이 구간은 최근 원문을 맡는다.
 *
 * 최근 것부터 [MAX_CHARS]까지 담고 말 중간에서 자르지 않는다([CardMessageWindow]와 같은 규칙). 상한을 두는
 * 이유는 대화방당 메시지 수에 제한이 없고, 이 구간이 **메시지를 보낼 때마다** 입력 토큰으로 과금되기 때문이다.
 *
 * 유저가 부른 캐릭터([pinned])의 가장 최근 발언은 구간 밖으로 밀렸어도 끼워 넣는다. 그 발언이 빠지면
 * 불린 캐릭터가 자기가 무슨 말을 했는지 모른 채 답하게 된다.
 */
class ConversationTranscript private constructor(
    val entries: List<TranscriptEntry>,
) {
    fun isEmpty(): Boolean = entries.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ConversationTranscript) {
            return false
        }
        return entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "ConversationTranscript(${entries.size} entries)"

    companion object {
        /** 구간의 크기(글자 수). 유저 메시지 상한(140자)으로 꽉 채워도 13마디, 실제 길이라면 30마디 안팎이 들어간다. */
        const val MAX_CHARS = 2000

        /**
         * 한 마디가 프롬프트에서 본문 말고 더 쓰는 글자 수. `"- eongttung: "`(가장 긴 화자 라벨)과 개행을 합한 값이다.
         * 본문만 재면 짧은 말이 많은 방에서 이 몫이 쌓여 실제 프롬프트가 상한을 넘는다.
         */
        private const val PER_ENTRY_OVERHEAD = 14

        val EMPTY = ConversationTranscript(emptyList())

        /** [entries](시간순)에서 최근 구간을 **시간순으로** 담는다. [pinned] 캐릭터의 가장 최근 발언은 항상 포함한다. */
        fun recent(
            entries: List<TranscriptEntry>,
            pinned: Collection<EmotionType> = emptySet(),
        ): ConversationTranscript {
            val normalized =
                entries
                    .map { it.copy(content = it.content.normalizeForPrompt()) }
                    .filter { it.content.isNotBlank() }
            val included = recentIndices(normalized) + latestIndicesOf(normalized, pinned)
            return ConversationTranscript(included.sorted().map { normalized[it] })
        }

        private fun recentIndices(entries: List<TranscriptEntry>): Set<Int> {
            val indices = mutableSetOf<Int>()
            var totalChars = 0
            for (index in entries.indices.reversed()) {
                totalChars += entries[index].content.length + PER_ENTRY_OVERHEAD
                if (totalChars > MAX_CHARS) {
                    break
                }
                indices.add(index)
            }
            return indices
        }

        private fun latestIndicesOf(
            entries: List<TranscriptEntry>,
            pinned: Collection<EmotionType>,
        ): Set<Int> =
            pinned
                .mapNotNull { character -> entries.indexOfLast { it.speaker == character }.takeIf { it >= 0 } }
                .toSet()
    }
}
