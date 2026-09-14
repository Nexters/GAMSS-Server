package com.nexters.gamss.llm.prompt

/**
 * [com.nexters.gamss.llm.settings.LlmSettings] 한 행이 어떤 용도의 프롬프트 설정인지 구분한다.
 * 행 생성 후 바뀌지 않는다. [COMMON]과 조립되는 것은 [COMMENT]/[REPLY]뿐이고 나머지는 단독으로 쓰인다
 * ([com.nexters.gamss.llm.settings.SystemPromptResolver]가 조립을 막는다). 모델은 타입별이 아니라 앱 전체
 * 단일 설정이다([com.nexters.gamss.llm.settings.LlmSettingsService]).
 */
enum class PromptType {
    /** 세 생성 타입이 공유하는 공통 프롬프트(톤·경계·말맛지침·보이스카드). */
    COMMON,

    /** 다중 캐릭터 댓글+티키타카 생성 (#4). */
    COMMENT,

    /** 유저가 캐릭터 댓글에 답글을 달았을 때 그 캐릭터만 재응답 (#14). */
    REPLY,

    /**
     * 대화 종료 시 그날 있었던 일을 유저 시점으로 적는 카드 한 줄 (#30, #111). 알아볼 수 있는 내용이 없는
     * 대화면 한 줄 대신 판정만 돌려준다([com.nexters.gamss.llm.generation.CardLineKind]).
     */
    CARD,

    /**
     * 엉뚱이가 다룰 소재 목록(한 줄에 소재 하나). 시스템 프롬프트 조립([COMMON] + 타입)에는
     * 쓰이지 않고, 생성 시 서버가 한 줄을 무작위로 골라 유저 콘텐츠에 넣는다
     * ([com.nexters.gamss.llm.selection.EongttungTopicSelector]).
     *
     * **사용자에게 그대로 보이기도 한다.** 알아볼 수 있는 내용이 없는 대화의 카드에는 고른 한 줄이 가공 없이
     * 카드 문구로 남는다([com.nexters.gamss.card.service.CardService]).
     */
    EONGTTUNG_TOPIC,

    /**
     * 카드 생성 요청에 emotion이 없을 때 유저 메시지만 보고 대표 감정 하나를 고르는 분류 (#134).
     * 분류 작업이라 캐릭터 톤·말맛 지침([COMMON])과 무관하므로 조립에 쓰이지 않고 단독으로 쓰인다
     * ([com.nexters.gamss.llm.generation.GeminiEmotionExtractor]).
     */
    CARD_EMOTION,
}
