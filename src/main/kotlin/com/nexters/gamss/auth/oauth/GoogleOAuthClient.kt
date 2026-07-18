package com.nexters.gamss.auth.oauth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class GoogleOAuthClient(
    private val verifier: OidcTokenVerifier,
) : OAuthClient {
    @Autowired
    constructor(properties: OAuthProperties) : this(
        OidcTokenVerifier(properties.google, JwkSources.remote(properties.google.jwksUri)),
    )

    override val provider = OAuthProvider.GOOGLE

    override fun verify(idToken: String): OAuthUserInfo = verifier.verify(idToken)
}
