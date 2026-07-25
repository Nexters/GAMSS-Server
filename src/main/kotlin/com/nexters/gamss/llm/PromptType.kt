package com.nexters.gamss.llm

/** [LlmSettings] 한 행이 어떤 용도의 모델·프롬프트 설정인지 구분한다. 행 생성 후 바뀌지 않는다. */
enum class PromptType {
    /** 다중 캐릭터 댓글+티키타카 생성 (#4). */
    COMMENT,

    /** 유저가 캐릭터 댓글에 답글을 달았을 때 그 캐릭터만 재응답 (#14). */
    REPLY,
}
