package com.nexters.gamss.auth.social

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.text.ParseException

/**
 * Firebase ID 토큰(RS256)을 검증한다. 서명·발급자·대상(aud)·만료를 확인하고 사용자 정보를 추출한다.
 *
 * Firebase 토큰은 issuer 가 `https://securetoken.google.com/{projectId}`, aud 가 `{projectId}` 이며,
 * 공개키는 구글 securetoken x509 엔드포인트에서 가져온다. 로그인 수단(google/apple)은
 * `firebase.sign_in_provider` 클레임에, 사용자 식별자는 `sub`(Firebase UID)에 담긴다.
 *
 * 공개키 소스를 주입받아 테스트에서 로컬 키로 대체할 수 있다.
 */
@Component
class FirebaseTokenVerifier(
    projectId: String,
    jwkSource: JWKSource<SecurityContext>,
) : SocialTokenVerifier {
    @Autowired
    constructor(properties: FirebaseProperties) : this(properties.projectId, FirebaseJwkSource())

    private val processor =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            jwtClaimsSetVerifier =
                DefaultJWTClaimsVerifier(
                    projectId,
                    JWTClaimsSet.Builder().issuer("https://securetoken.google.com/$projectId").build(),
                    setOf("sub", "iat", "exp"),
                )
        }

    override fun verify(idToken: String): SocialUser {
        val claims = parseClaims(idToken)
        val uid = claims.subject ?: throw BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN)
        val provider = SocialProvider.fromFirebase(signInProvider(claims))
        return SocialUser(
            uid = uid,
            provider = provider,
            email = claims.getStringClaim("email"),
            name = claims.getStringClaim("name"),
        )
    }

    private fun signInProvider(claims: JWTClaimsSet): String? {
        val firebase = runCatching { claims.getJSONObjectClaim("firebase") }.getOrNull()
        return firebase?.get("sign_in_provider") as? String
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
