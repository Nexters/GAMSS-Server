package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Component

/**
 * 등록된 OAuthClient 들을 provider 로 매핑한다(일급 컬렉션).
 * 새 제공자 구현체(@Component)를 추가하면 자동으로 등록된다(OCP).
 */
@Component
class OAuthClientResolver(
    clients: List<OAuthClient>,
) {
    private val clientsByProvider: Map<OAuthProvider, OAuthClient> = clients.associateBy { it.provider }

    fun resolve(provider: OAuthProvider): OAuthClient =
        clientsByProvider[provider]
            ?: throw BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER)
}
