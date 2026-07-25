package com.nexters.gamss.llm

/**
 * [LlmSettings] 한 행이 어떤 용도의 프롬프트 설정인지 구분한다. 행 생성 후 바뀌지 않는다.
 * 실제 시스템 프롬프트는 [COMMON] + 생성 타입([COMMENT]/[REPLY]/[CARD])으로 조립된다.
 *
 * [usesModel]은 그 타입이 자체 모델을 갖는지다 — [COMMON]은 프롬프트 조각일 뿐이라 모델이 없다.
 * 타입별 분기(`when`/`if`) 대신 이 데이터로 동작을 표현해 새 타입 추가에 열려 있게 한다.
 */
enum class PromptType(
    val usesModel: Boolean,
) {
    /** 세 생성 타입이 공유하는 공통 프롬프트(톤·경계·말맛지침·보이스카드). 모델은 쓰지 않는다. */
    COMMON(usesModel = false),

    /** 다중 캐릭터 댓글+티키타카 생성 (#4). */
    COMMENT(usesModel = true),

    /** 유저가 캐릭터 댓글에 답글을 달았을 때 그 캐릭터만 재응답 (#14). */
    REPLY(usesModel = true),

    /** 대화 종료 시 대표 캐릭터가 유저를 대신해 남기는 카드 한 줄 (#30). */
    CARD(usesModel = true),
}
