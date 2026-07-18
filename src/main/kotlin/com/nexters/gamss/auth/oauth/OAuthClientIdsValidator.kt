package com.nexters.gamss.auth.oauth

import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * dev/prod 기동 시 OAuth client-ids 가 비어 있으면 부팅을 실패시킨다.
 *
 * client-ids 가 비면 aud 검증이 fail-closed 로 막혀 모든 로그인이 실패한다(AllowedAudiences).
 * 그 상태로 배포된 걸 런타임에야 발견하지 않도록, 설정 누락을 기동 시점에 크게 드러낸다 —
 * "모든 로그인 실패"보다 "서버가 안 뜸"이 더 빨리 잡힌다.
 *
 * local/test 프로필은 검증 없이 뜰 수 있게 둔다(설정 없이 로컬 구동·테스트 허용).
 */
@Component
class OAuthClientIdsValidator(
    private val properties: OAuthProperties,
    private val environment: Environment,
) : InitializingBean {
    override fun afterPropertiesSet() {
        if (isConfigOptional()) {
            return
        }
        require(properties.google.clientIds.isNotEmpty()) { missingMessage("GOOGLE_CLIENT_IDS") }
        require(properties.apple.clientIds.isNotEmpty()) { missingMessage("APPLE_CLIENT_IDS") }
    }

    private fun isConfigOptional(): Boolean {
        val active = environment.activeProfiles
        return active.isEmpty() || active.all { it in CONFIG_OPTIONAL_PROFILES }
    }

    private fun missingMessage(key: String): String =
        "$key 가 비어 있습니다. 활성 프로필(${environment.activeProfiles.joinToString()})에서는 " +
            "소셜 로그인 aud 검증을 위해 필수입니다."

    companion object {
        private val CONFIG_OPTIONAL_PROFILES = setOf("local", "test")
    }
}
