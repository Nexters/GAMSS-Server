package com.nexters.gamss.auth.oauth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AppleOAuthClient(
    private val verifier: OidcTokenVerifier,
) : OAuthClient {
    @Autowired
    constructor(properties: OAuthProperties) : this(
        OidcTokenVerifier(properties.apple, JwkSources.remote(properties.apple.jwksUri)),
    )

    override val provider = OAuthProvider.APPLE

    override fun verify(idToken: String): OAuthUserInfo = verifier.verify(idToken)
}
