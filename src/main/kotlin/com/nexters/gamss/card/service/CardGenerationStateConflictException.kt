package com.nexters.gamss.card.service

/**
 * 카드 생성 상태를 PENDING에서 DONE으로 옮기는 CAS가 기대와 다르게 0건을 갱신했을 때 던지는 예외.
 * 채팅방 삭제, PENDING 타임아웃 리셋 등 원인이 여러 가지라 [CardService]가 원인을 다시 조회해
 * 적절한 응답으로 변환한다.
 */
class CardGenerationStateConflictException(
    message: String,
) : IllegalStateException(message)
