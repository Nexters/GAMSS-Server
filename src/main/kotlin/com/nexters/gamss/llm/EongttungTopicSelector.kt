package com.nexters.gamss.llm

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 엉뚱이가 다룰 "소재"를 서버가 미리 골라서 프롬프트에 박아 넣는다. LLM에게 "아무 얘기나 뜬금없이
 * 해봐"라고 맡기면 [CharacterSelector]에서 이미 확인한 것과 같은 문제(LLM의 "무작위"는 통계적으로
 * 균등하지 않고 특정 소재로 수렴하기 쉬움)가 그대로 반복된다 — 이 프롬프트 자체도 소재를 "user 메시지에
 * 별도로 주어진다"고 전제하고 있어 LLM 즉흥 생성을 상정하지 않는다.
 *
 * TODO: 초기 목록(placeholder)이라 팀 리뷰·실사용 데이터로 다듬을 필요가 있다.
 */
@Component
class EongttungTopicSelector(
    private val random: Random = Random.Default,
) {
    fun select(): String = TOPICS.random(random)

    companion object {
        private val TOPICS =
            listOf(
                "오늘 점심에 뭘 먹을지",
                "발톱을 언제 깎아야 할지",
                "라면 물의 정확한 양",
                "신발끈이 자꾸 풀리는 이유",
                "편의점 삼각김밥 유통기한",
                "엘리베이터 버튼을 두 번 누르는 사람들",
                "귤 까는 가장 빠른 방법",
                "겨울에 손이 트는 이유",
                "버스 정류장 벤치 색깔",
                "무릎 나온 바지의 운명",
            )
    }
}
