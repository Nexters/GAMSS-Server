package com.nexters.gamss.card.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

/**
 * 카드 도메인 설정.
 *
 * [autoCardStartDate]는 자동 종료·카드 생성 배치가 다루는 가장 이른 대화방 생성일(KST)이다.
 * **요약 저장이 배포된 날 이후로 잡는다** — 그 전에 만들어진 방은 요약이 없어(요약 저장 자체가
 * 이 기능부터다) 배치가 카드는 못 만들고 종료만 시켜버린다. 사용자는 이어쓰기만 잃고 얻는 게 없다.
 *
 * 늦게 잡는 쪽이 안전하다 — 하한과 배포일 사이에 만들어진 방은 배치가 손대지 않고 그대로 남을 뿐이지만,
 * 이르게 잡으면 요약 없는 방들이 카드 없이 닫힌다.
 */
@ConfigurationProperties(prefix = "card")
data class CardProperties(
    val autoCardStartDate: LocalDate,
)
