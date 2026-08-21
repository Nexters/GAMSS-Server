package com.nexters.gamss.card.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 카드 공유 링크 발급 결과. 앱은 이 URL 을 그대로 공유 시트에 넘기면 된다.
 *
 * 토큰이 아니라 완성된 URL 을 준다 — 앱이 도메인을 들고 있지 않아도 되고, 도메인이 바뀌어도
 * 앱 업데이트 없이 따라온다.
 */
data class CardShareLinkResponse(
    @field:Schema(
        description = "카드 공유 링크. 같은 카드는 몇 번을 요청해도 같은 URL 이다.",
        example = "https://gamss.kr/c/Zm9vYmFyYmF6cXV4MTIzNA",
    )
    val shareUrl: String,
)
