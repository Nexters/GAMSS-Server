package com.nexters.gamss.auth.oauth

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import java.net.URI

/**
 * 원격 JWKS 엔드포인트로부터 공개키를 가져오는 JWKSource 생성기(캐싱 포함).
 */
object JwkSources {
    fun remote(uri: String): JWKSource<SecurityContext> = JWKSourceBuilder.create<SecurityContext>(URI(uri).toURL()).build()
}
