package com.nexters.gamss.auth.oauth

/**
 * 허용된 대상(aud) 목록 일급 컬렉션. 토큰의 audience 가 허용 목록에 포함되는지 판단한다.
 */
class AllowedAudiences(
    private val values: List<String>,
) {
    fun accepts(audiences: List<String>?): Boolean {
        if (values.isEmpty()) {
            return true
        }
        return !audiences.isNullOrEmpty() && audiences.any { it in values }
    }
}
