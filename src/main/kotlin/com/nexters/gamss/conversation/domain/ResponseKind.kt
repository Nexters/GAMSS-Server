package com.nexters.gamss.conversation.domain

/** 캐릭터 응답이 어떤 생성으로 만들어졌는지. 일기에 달린 댓글 묶음([COMMENT])인지, 유저 답장에 대한 재응답([REPLY])인지. */
enum class ResponseKind {
    COMMENT,
    REPLY,
}
