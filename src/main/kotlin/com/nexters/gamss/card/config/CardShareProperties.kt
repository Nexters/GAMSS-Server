package com.nexters.gamss.card.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 카드 공유 링크 설정.
 *
 * [CardProperties] 와 나눠 둔다 — 그쪽은 자동 카드 배치가 언제부터의 방을 다루느냐는 설정이라
 * 바뀔 이유가 공유와 전혀 다르다. 한 클래스에 두면 공유 설정이 하나 늘 때마다 배치 쪽을 쓰는
 * 코드까지 함께 손봐야 한다(실제로 배치 테스트 3개가 깨졌다).
 *
 * [baseUrl]은 공유 링크의 앞부분이다. 서버가 완성된 URL 을 내려주므로 앱이 도메인을 들고 있지
 * 않아도 되고, 도메인이 바뀌어도 앱 업데이트 없이 따라온다.
 */
@ConfigurationProperties(prefix = "card.share")
data class CardShareProperties(
    val baseUrl: String,
)
