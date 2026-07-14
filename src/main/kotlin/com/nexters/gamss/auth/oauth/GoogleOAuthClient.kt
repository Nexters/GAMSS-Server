package com.nexters.gamss.auth.oauth

import com.nexters.gamss.member.domain.OAuthProvider
import org.springframework.stereotype.Component

@Component
class GoogleOAuthClient(
    properties: OAuthProperties,
) : OAuthClient {
    override val provider = OAuthProvider.GOOGLE
    private val verifier = OidcTokenVerifier(properties.google, JwkSources.remote(properties.google.jwksUri))

    override fun verify(idToken: String): OAuthUserInfo = verifier.verify(idToken)
}
