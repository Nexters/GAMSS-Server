package com.nexters.gamss.llm.prompt

/**
 * [com.nexters.gamss.llm.settings.LlmSettings] 한 행이 어떤 용도의 프롬프트 설정인지 구분한다.
 * 행 생성 후 바뀌지 않는다. 실제 시스템 프롬프트는 [COMMON] + 생성 타입([COMMENT]/[REPLY]/[CARD])으로
 * 조립된다. 모델은 타입별이 아니라 앱 전체 단일 설정이다([com.nexters.gamss.llm.settings.LlmSettingsService]).
 */
enum class PromptType {
    /** 세 생성 타입이 공유하는 공통 프롬프트(톤·경계·말맛지침·보이스카드). */
    COMMON,

    /** 다중 캐릭터 댓글+티키타카 생성 (#4). */
    COMMENT,

    /** 유저가 캐릭터 댓글에 답글을 달았을 때 그 캐릭터만 재응답 (#14). */
    REPLY,

    /** 대화 종료 시 대표 캐릭터가 유저를 대신해 남기는 카드 한 줄 (#30). */
    CARD,

    /**
     * 엉뚱이가 다룰 소재 목록(한 줄에 소재 하나). 시스템 프롬프트 조립([COMMON] + 타입)에는
     * 쓰이지 않고, 생성 시 서버가 한 줄을 무작위로 골라 유저 콘텐츠에 넣는다
     * ([com.nexters.gamss.llm.selection.EongttungTopicSelector]).
     */
    EONGTTUNG_TOPIC,
}
