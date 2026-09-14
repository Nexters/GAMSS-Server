package com.nexters.gamss.llm.generation

/**
 * 카드 한 줄 생성기가 입력을 보고 내린 판정. 카드의 한 줄을 누가 말하는지가 여기서 갈린다.
 *
 * 판정을 따로 받는 이유는 LLM에게 "쓸 사건이 없다"고 답할 길을 열어 두기 위해서다. 입력이 무엇이든
 * 사건 하나를 고르게 하면, 알아볼 수 있는 내용이 없는 대화(자모 나열, 키보드 연타)에서도 LLM이 없는
 * 사건을 지어낸다.
 */
enum class CardLineKind {
    /** 유저 시점의 하루 기록으로 쓸 수 있다. 사건 없이 기분이나 상태만 드러나도 여기다. */
    EVENT,

    /**
     * 알아볼 수 있는 내용 자체가 없다. 사건을 지어내는 대신 엉뚱이 소재 목록에서 고른 한 줄을 그대로 남긴다
     * ([com.nexters.gamss.card.service.CardService]).
     */
    NONSENSE,
}
