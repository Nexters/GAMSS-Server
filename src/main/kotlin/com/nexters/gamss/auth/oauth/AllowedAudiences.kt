package com.nexters.gamss.auth.oauth

/**
 * 허용된 대상(aud) 목록 일급 컬렉션. 토큰의 audience 가 허용 목록에 포함되는지 판단한다.
 *
 * 허용 목록이 비어 있으면(설정 누락) 어떤 audience 도 통과시키지 않는다(fail-closed).
 * 설정을 빠뜨렸을 때 aud 검증이 조용히 꺼지는 대신 로그인이 막혀 문제가 드러나도록 한다.
 * dev/prod 에서 목록이 비는 것은 OAuthClientIdsValidator 가 기동 시점에 먼저 막는다.
 */
class AllowedAudiences(
    private val values: List<String>,
) {
    fun accepts(audiences: List<String>?): Boolean = !audiences.isNullOrEmpty() && audiences.any { it in values }
}
