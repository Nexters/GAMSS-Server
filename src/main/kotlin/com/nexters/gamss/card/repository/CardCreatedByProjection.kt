package com.nexters.gamss.card.repository

import com.nexters.gamss.card.domain.CardCreatedBy

/** 대화방별 카드 생성 주체(백오피스 대화방 사용량). Spring Data 인터페이스 프로젝션. */
interface CardCreatedByProjection {
    val conversationId: Long
    val createdBy: CardCreatedBy?
}
