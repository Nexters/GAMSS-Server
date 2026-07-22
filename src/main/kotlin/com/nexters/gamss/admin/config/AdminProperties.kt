package com.nexters.gamss.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 백오피스 접근 허용 관리자 목록. 구글 로그인 후 이 목록에 이메일이 있어야 관리자 토큰을 발급한다.
 * 비밀이 아니므로 설정값(env `ADMIN_EMAILS`, 쉼표 구분)으로 둔다.
 *
 * 이메일 비교는 공백·대소문자를 무시한다(구글이 돌려주는 형태가 항상 같다고 보장되지 않음).
 */
@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val emails: List<String> = emptyList(),
) {
    private val allowed: Set<String> = emails.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()

    fun isAllowed(email: String): Boolean = normalize(email) in allowed

    /** 이메일 비교·저장에 쓰는 정규화(공백 제거·소문자). 토큰 발급 등 다른 곳에서도 재사용한다. */
    fun normalize(email: String): String = email.trim().lowercase()
}
