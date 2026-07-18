package com.nexters.gamss.auth.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Firebase(securetoken) x509 인증서 파싱 검증.
 *
 * 실제 엔드포인트와 같은 `{kid: PEM인증서}` 형식을 파싱해, 그 공개키로 짝이 되는 개인키가
 * 서명한 토큰의 검증까지 성공하는지 확인한다(파싱 → 공개키 → 서명 검증 전체 경로).
 * 키 쌍은 openssl 로 생성한 테스트용 자체서명 인증서다.
 */
class FirebaseJwkSourceTest {
    private val kid = "test-kid-1"

    private val certPem =
        """
        -----BEGIN CERTIFICATE-----
        MIIDDTCCAfWgAwIBAgIUa+4Mj2jlNanX8TYZqhi+/dSDak4wDQYJKoZIhvcNAQEL
        BQAwFjEUMBIGA1UEAwwLc2VjdXJldG9rZW4wHhcNMjYwNzE4MTUyNTQ4WhcNMjYw
        NzE5MTUyNTQ4WjAWMRQwEgYDVQQDDAtzZWN1cmV0b2tlbjCCASIwDQYJKoZIhvcN
        AQEBBQADggEPADCCAQoCggEBAP5fA2WNud4M/NSPXmughDkY0j9ERpVkYgLHXnQw
        QN0yjYLnjN4SN4zL9Ef2f0OF+gbtWFWi4xLhGOlXEW+pZZuiNg9YrSVOxGi0+efg
        4xr+yJ2LeFWaN7Tcn8B0otLAj4HRAJj5febrlH+DEkMVYJA9d5aWSDGSOKOEXtFu
        ujw7S8q//txdgypxD3AJ6If/ZzjtCxeGROmjpvap3sl0fka+yXOURWpsWcHzs4cw
        0zZN1dk6A8YAMO7t+HnCnCXysRLZGHm/ifS/pXoVOBz4yEb88q+nJyj9U6MSzH3O
        jNWwI2JhALkHr1TPuhqRYqg2eRfR632pmtpcSnBR4TGFBeUCAwEAAaNTMFEwHQYD
        VR0OBBYEFG89V/aLdAYie9arGOJ0kDTdD8ONMB8GA1UdIwQYMBaAFG89V/aLdAYi
        e9arGOJ0kDTdD8ONMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEB
        AIzd2ipsEA/U7M4Ig7ReBvhSPSYguPSSOk34zfUgMS3kg7dPCAD0xdKVyj5/nR78
        gvaFBqsFNtTjDWQoJk98YtdPvtvq/oHkCB5sH2LwNQQDEjJFo4lt+sd7SDiwUs8E
        jnbAVqkpx1SWIoFi9zSR6PJfJAglHw3p3yFSDmPDCXnemQMzIhl6XVukit550dT9
        IGfCm4k9zNP0oNyIi83J/g3CIBXZtIVFUcI9m3sVNOKWQQcBFYMkik4w+4ia8qXp
        zjOdQH0AMzrbVO0e6DnTocBwAXnh+48DWiI2QLCdoxU+OXjpV6eWUUVVn/XarCrH
        jZxpHVuDeBNSAhQ0IUq5BLo=
        -----END CERTIFICATE-----
        """.trimIndent()

    private val privateKeyPem =
        """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD+XwNljbneDPzU
        j15roIQ5GNI/REaVZGICx150MEDdMo2C54zeEjeMy/RH9n9DhfoG7VhVouMS4Rjp
        VxFvqWWbojYPWK0lTsRotPnn4OMa/sidi3hVmje03J/AdKLSwI+B0QCY+X3m65R/
        gxJDFWCQPXeWlkgxkjijhF7Rbro8O0vKv/7cXYMqcQ9wCeiH/2c47QsXhkTpo6b2
        qd7JdH5GvslzlEVqbFnB87OHMNM2TdXZOgPGADDu7fh5wpwl8rES2Rh5v4n0v6V6
        FTgc+MhG/PKvpyco/VOjEsx9zozVsCNiYQC5B69Uz7oakWKoNnkX0et9qZraXEpw
        UeExhQXlAgMBAAECggEAHiHR3IUjZWMTIs2GQ9Lp7Cbn5kgHjHQttWKPP82PgXnB
        kFDZkQeGE7mn9aEE26d9Qgt6O/q6/LuJdeqzIrplp5VZjgvVHDxDwUY0QabpgC3P
        mfSxi9e5jZqCRLa3AFGAg8I8WI1awAYGu0F4N6t1RYuNJQYnBdLqaVmphGmiH2GH
        raU/a7gQtyUj18W6omySeClgWORdXSM3NcrhIYcilaQC17x+lGQlOUga7n7PnOM8
        kAmGeu9RXgbi1p9OJ0Eyl38ZE+07qrXnfof0tZwlbY2hSsS821EGDmShFLOLh8Be
        ofCMDpXMM4sRasiOY2ezXJTIS+bC58erhJ6h7C4gqQKBgQD/64wFK+uLSFyqjShQ
        6iwSagVJkTqCmSOThk6e1iaR9ZlfdDbZE0/V6EBhHxm1Q5tTRKSW6ilzR71/vpHU
        CEygY9r8tF+CRZpfg3z19K4l5w+VQ8+vpTondEQ/9ulpHi0cnSq4DKcCF0Toi8MO
        suHv5BO47kioxM+iScMiJm/+ZwKBgQD+c1evh0PEKNmTv1NjbBSjJ+Z8cWjx50+t
        qkfOyXXuAZFLRczvnp9UpfkXUQ9UyQg+GmrepzHPLHfS9+0+hgExTnNUGna1Zq32
        CchcnjpPZW6lJBBffBgSgM4rqrrV1HIWGtJS/YYy7EVL5uU9g6ZP3ABW5gaFqi8j
        sHpv/DKR0wKBgQDh5BBwlCbRhyq8I+J9IWWLT2ehWv4xPmjHk5ob21yqIwWg2px7
        GO+0GM+SqnfvpmAYrJM23jN+HMmoUxa2+ChiviESQ0e2Xid3vUD0fGem7v+zOeq8
        1/Ov1ZFUgGXgMhGRmyyUzh4v20golwyyEbUaINBDwJgD18yKK9+AajsaawKBgQC4
        asqTqnrAEkdXoSSD+5Kdic6wRNYl9Vs3aCHxSuxRGwu+PZHB3fpmtIBrmNF+xtFV
        iXoJc65TFEyoOA7X8PGuVcianS+KXCgbGY4BKqDiaIaf5BqFyf/8cSR8W5pcXkRt
        pvTnN15rS/kRgQCG7AK/AJmyYbc6a1UNDdw/i87vBwKBgE0Lr4nyzjiQlgeudBhB
        uuzCMJ6fLdH2Ku9YP7BoArJX/cS7eJ5UcqORQEYQnaD7MNOVc/3MLNGpwzu9L3nk
        QOdNmOW48h6BbDm6luzlRw54t6MMPLdurTvtrdRy7bLPtca+qGs2L06KqSbwcVoc
        lGt+l0fJwVZAtqcvdcptQXAo
        -----END PRIVATE KEY-----
        """.trimIndent()

    private fun certsJson(): String = "{\"$kid\": \"${certPem.replace("\n", "\\n")}\"}"

    @Test
    fun `x509 인증서 JSON을 kid로 조회 가능한 RSA 공개키로 파싱한다`() {
        val jwkSet = FirebaseJwkSource.toJwkSet(certsJson())

        assertEquals(1, jwkSet.keys.size)
        assertEquals(kid, jwkSet.keys[0].keyID)
        assertTrue(jwkSet.keys[0] is RSAKey)
    }

    @Test
    fun `파싱한 공개키로 짝이 되는 개인키가 서명한 토큰을 검증한다`() {
        val jwkSource = ImmutableJWKSet<SecurityContext>(FirebaseJwkSource.toJwkSet(certsJson()))
        val verifier = FirebaseTokenVerifier("gamss-cbdcb", jwkSource)

        val user = verifier.verify(signedToken())

        assertEquals("firebase-uid-1", user.uid)
        assertEquals(OAuthProvider.GOOGLE, user.provider)
    }

    private fun signedToken(): String {
        val keyBytes =
            Base64.getDecoder().decode(
                privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace(Regex("\\s"), ""),
            )
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val claims =
            JWTClaimsSet
                .Builder()
                .subject("firebase-uid-1")
                .issuer("https://securetoken.google.com/gamss-cbdcb")
                .audience("gamss-cbdcb")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .issueTime(Date())
                .claim("firebase", mapOf("sign_in_provider" to "google.com"))
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims)
        jwt.sign(RSASSASigner(privateKey))
        return jwt.serialize()
    }
}
