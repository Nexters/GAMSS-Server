package com.nexters.gamss.auth.oauth

import com.nexters.gamss.member.domain.OAuthProvider
import org.springframework.stereotype.Component

@Component
class AppleOAuthClient(
    properties: OAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.APPLE
    private val verifier = OidcTokenVerifier(properties.apple, JwkSources.remote(properties.apple.jwksUri))

    override fun verify(idToken: String): OAuthUserInfo = verifier.verify(idToken)
}
