package com.nexters.gamss.llm.selection

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component

/**
 * 부르는 말(호격)로 호명을 판정하는 규칙 기반 구현. 캐릭터 이름이 기쁨, 슬픔, 불안처럼 일상어라서
 * **정밀도를 재현율보다 우선한다.** 놓치면 지금처럼 무작위 캐릭터가 반응할 뿐이지만, 잘못 잡으면 부르지도
 * 않은 캐릭터 혼자 답해 대화가 어색해진다.
 *
 * 호명으로 보는 형태:
 * - 어디서든: 받침 있는 이름 + "아"("기쁨아"). "기쁨아니"처럼 뒤에 글자가 붙으면 제외한다.
 * - 문장 맨 앞에서만: "기쁨이야"(받침), "분노야"(받침 없음), 이름 뒤 바로 쉼표나 느낌표("기쁨이, ", "분노!").
 *   이 형태들은 문장 중간에서 "그건 분노야"처럼 서술어로도 쓰여 맨 앞에서만 받는다.
 *
 * 호명으로 보지 않는 형태: "기쁨이 넘치는 하루"처럼 이름 뒤에 조사 "이"와 공백만 오는 경우. "기쁨이 그게 뭐야"와
 * 문법상 구분할 수 없어 버린다. 같은 이유로 "슬픔이 어디 갔어?", "슬픔이는?"처럼 찾는 말도 잡지 않는다. 막아둔
 * 캐릭터를 이런 말로 찾는 경우는 프롬프트에 막아둔 캐릭터를 항상 싣는 쪽이 맡는다
 * ([com.nexters.gamss.llm.prompt.CommentPromptContext.excludedCharacters]).
 *
 * 알고 감수하는 오탐: "오늘 좋았다. 기쁨이야"처럼 문장 끝 뒤에 서술어 "기쁨이야"가 새 문장으로 오면 호명으로 본다.
 * 문장 맨 앞의 "기쁨이야 그게 뭐야"와 구분할 수 없고, 서술어로 새 문장을 여는 경우는 드물어서 받는다.
 */
@Component
class VocativeAddresseeResolver : AddresseeResolver {
    private val patterns: Map<EmotionType, List<Regex>> = EmotionType.entries.associateWith { patternsFor(it.label) }

    override fun resolve(message: String): List<EmotionType> =
        patterns
            .mapNotNull { (character, regexes) ->
                regexes.mapNotNull { it.find(message)?.range?.first }.minOrNull()?.let { character to it }
            }.sortedBy { it.second }
            .map { it.first }

    private fun patternsFor(name: String): List<Regex> {
        val quoted = Regex.escape(name)
        if (hasFinalConsonant(name)) {
            return listOf(
                Regex("(?<!$HANGUL)${quoted}아(?!$HANGUL)"),
                Regex("$SENTENCE_START${quoted}이야(?!$HANGUL)"),
                Regex("$SENTENCE_START${quoted}이?(?=\\s*$CALL_MARK)"),
            )
        }
        return listOf(
            Regex("$SENTENCE_START${quoted}야(?!$HANGUL)"),
            Regex("$SENTENCE_START$quoted(?=\\s*$CALL_MARK)"),
        )
    }

    /** 마지막 글자에 받침이 있는지. 한글 음절은 (코드 - 0xAC00) % 28 이 종성 번호이고, 0이면 받침이 없다. */
    private fun hasFinalConsonant(name: String): Boolean = (name.last() - HANGUL_SYLLABLE_START) % FINAL_CONSONANT_COUNT != 0

    companion object {
        private const val HANGUL = "[가-힣]"
        private const val SENTENCE_START = "(?:^|[.!?~\\n])\\s*"
        private const val CALL_MARK = "[,!?~]"
        private const val HANGUL_SYLLABLE_START = '가'
        private const val FINAL_CONSONANT_COUNT = 28
    }
}
