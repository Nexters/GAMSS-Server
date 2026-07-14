package com.nexters.gamss.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    val google: Provider,
    val apple: Provider,
) {
    data class Provider(
        val issuer: String,
        val jwksUri: String,
        val clientIds: List<String> = emptyList(),
    )
}
