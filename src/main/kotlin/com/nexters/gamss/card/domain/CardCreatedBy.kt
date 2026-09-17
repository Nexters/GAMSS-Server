package com.nexters.gamss.card.domain

/** 카드를 누가 만들었는지. 과거 카드는 값이 없을 수 있다(NULL = 알 수 없음, 백필 불가). */
enum class CardCreatedBy {
    USER,
    AUTO_BATCH,
}
