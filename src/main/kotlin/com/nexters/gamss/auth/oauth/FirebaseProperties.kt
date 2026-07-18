package com.nexters.gamss.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Firebase 프로젝트 설정. 토큰의 issuer·audience 가 이 프로젝트 ID로 결정된다.
 * 프로젝트 ID는 비밀이 아니므로 시크릿이 아닌 설정값으로 둔다.
 */
@ConfigurationProperties(prefix = "firebase")
data class FirebaseProperties(
    val projectId: String,
)
