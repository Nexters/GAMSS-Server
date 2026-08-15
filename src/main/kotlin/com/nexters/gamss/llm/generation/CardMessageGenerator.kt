package com.nexters.gamss.llm.generation

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 클라이언트가 만든 대화 요약을 다듬어, 카드에 남길 하루 기록 한 줄을 생성한다.
 * 구현(Gemini 등)은 교체 가능하다([CommentGenerator]와 같은 패턴).
 *
 * 클라이언트도 요약을 보내지만 그 값은 온프레미스 모델 산출물이라 문장이 투박하다. 카드에 그대로
 * 실으면 눈에 띄므로 서버 LLM이 한 번 다듬는다. [emotion]은 문장에 넣을 단어가 아니라 어느 사건을
 * 고를지의 기준이다 — 카드 한 줄에 캐릭터 말투는 쓰지 않는다.
 *
 * 시스템 프롬프트는 CARD 타입 원본을 그대로 쓴다(COMMON과 조립하지 않는다). 캐릭터 보이스 카드를
 * 앞에 붙이면 "말투를 지켜라"와 "말투를 쓰지 마라"가 충돌하기 때문이다
 * ([com.nexters.gamss.llm.settings.SystemPromptResolver]가 조립을 막는다).
 */
interface CardMessageGenerator {
    fun generate(
        emotion: EmotionType,
        summary: String,
    ): CardMessageOutput
}
