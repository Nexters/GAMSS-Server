package com.nexters.gamss.auth.oauth

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.text.ParseException

/**
 * OIDC id_token(RS256)의 서명·발급자·대상(aud)·만료를 검증하고 사용자 정보를 추출한다.
 * JWKS 소스를 주입받아 테스트에서 로컬 키로 대체할 수 있다.
 */
class OidcTokenVerifier(
    provider: OAuthProperties.Provider,
    jwkSource: JWKSource<SecurityContext>,
) {
    private val allowedAudiences = AllowedAudiences(provider.clientIds)
    private val processor =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    JWTClaimsSet.Builder().issuer(provider.issuer).build(),
                    setOf("exp"),
                )
        }

    fun verify(idToken: String): OAuthUserInfo {
        val claims = parseClaims(idToken)
        if (!allowedAudiences.accepts(claims.audience)) {
            throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        }
        val subject = claims.subject ?: throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        return OAuthUserInfo(providerId = subject, email = claims.getStringClaim("email"))
    }

    private fun parseClaims(idToken: String): JWTClaimsSet =
        try {
            processor.process(idToken, null)
        } catch (e: ParseException) {
            throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        } catch (e: BadJOSEException) {
            throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        } catch (e: JOSEException) {
            throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        }
}
