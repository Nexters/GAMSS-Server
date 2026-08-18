package com.nexters.gamss.notification.service

/**
 * 트랜잭션 안에서 푸시 발송을 부른 것 — 발송 실패가 아니라 **코드가 잘못됐다는 신호**다.
 *
 * 타입을 따로 두는 이유는 받는 쪽이 이것만 골라낼 수 있어야 하기 때문이다. [IllegalStateException]
 * 으로 던지면 같은 타입을 쓰는 다른 예외
 * ([com.nexters.gamss.card.service.CardGenerationStateConflictException] 가 그렇다)와 구분되지 않고,
 * 잘못 분류했을 때의 대가가 크다 — 카드 생성 배치는 이 예외를 삼키지 않고 그대로 올려보내 그날
 * 실행을 중단시킨다.
 */
class PushInTransactionException(
    message: String,
) : IllegalStateException(message)
