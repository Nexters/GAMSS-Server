package com.nexters.gamss.auth.oauth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * dev/prod 기동 시 OAuth client-ids 가 비어 있으면 경고 로그를 남긴다.
 *
 * client-ids 가 비면 aud 검증이 fail-closed 로 막혀 모든 소셜 로그인이 실패한다(AllowedAudiences).
 * 설정 누락을 기동 시점에 크게 드러내되, 배포 파이프라인 검증 등을 위해 부팅 자체는 막지 않는다 —
 * client-ids 가 채워지기 전까지 소셜 로그인만 안 되고 서버는 뜬다.
 * local/test 프로필은 검사 대상에서 제외한다(설정 없이 로컬 구동·테스트 허용).
 */
@Component
class OAuthClientIdsValidator(
    private val properties: OAuthProperties,
    private val environment: Environment,
) : InitializingBean {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterPropertiesSet() {
        if (isConfigOptional()) {
            return
        }
        if (properties.google.clientIds.isEmpty()) {
            log.warn(missingMessage("GOOGLE_CLIENT_IDS"))
        }
        if (properties.apple.clientIds.isEmpty()) {
            log.warn(missingMessage("APPLE_CLIENT_IDS"))
        }
    }

    private fun isConfigOptional(): Boolean {
        val active = environment.activeProfiles
        return active.isEmpty() || active.all { it in CONFIG_OPTIONAL_PROFILES }
    }

    private fun missingMessage(key: String): String =
        "$key 가 비어 있습니다. 활성 프로필(${environment.activeProfiles.joinToString()})에서는 " +
            "소셜 로그인 aud 검증이 막혀 모든 소셜 로그인이 실패합니다. 배포 전 반드시 설정하세요."

    companion object {
        private val CONFIG_OPTIONAL_PROFILES = setOf("local", "test")
    }
}
