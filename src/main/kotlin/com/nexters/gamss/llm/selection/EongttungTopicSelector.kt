package com.nexters.gamss.llm.selection

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 엉뚱이가 다룰 "소재"를 서버가 미리 골라서 프롬프트에 박아 넣는다. LLM에게 "아무 얘기나 뜬금없이
 * 해봐"라고 맡기면 [CharacterSelector]에서 이미 확인한 것과 같은 문제(LLM의 "무작위"는 통계적으로
 * 균등하지 않고 특정 소재로 수렴하기 쉬움)가 그대로 반복된다 — 이 프롬프트 자체도 소재를 "user 메시지에
 * 별도로 주어진다"고 전제하고 있어 LLM 즉흥 생성을 상정하지 않는다.
 */
@Component
class EongttungTopicSelector(
    private val random: Random = Random.Default,
) {
    fun select(): String = TOPICS.random(random)

    companion object {
        private val TOPICS =
            listOf(
                "오늘따라 유독 피곤하다",
                "배고프다",
                "커피 마실지 말지 고민된다",
                "옷 사고싶다",
                "뭐 맛있는거 없나",
                "집가고싶다",
                "택배 시킨거 언제오지",
                "심심하다",
                "시간이 안간다",
                "노래 뭐 듣지",
                "목마르다",
                "눈이 뻑뻑하다",
                "하품이 계속 나온다",
                "단 거 땡긴다",
                "뭔가 깜빡한 것 같다",
                "유튜브나 볼까",
                "뭔가 재밌는 거 없나",
                "귀찮다, 손가락 하나 까딱하기 싫다",
                "아무것도 하기 싫다",
                "폰 어디 뒀지",
                "일어나기 싫다",
                "온몸이 다 무겁다",
                "아까부터 계속 딴생각만 든다",
                "눈이 자꾸 감긴다",
                "안경 어디 뒀는지 모르겠다",
                "요즘 볼 영화 없나",
                "요즘 스팸전화가 왜 이렇게 많이 오지",
                "폰 바꾸고 싶다",
                "마라탕 땡긴다",
                "로또 당첨됐으면 좋겠다",
            )
    }
}
