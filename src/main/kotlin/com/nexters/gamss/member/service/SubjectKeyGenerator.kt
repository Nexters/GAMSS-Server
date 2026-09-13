package com.nexters.gamss.member.service

import com.nexters.gamss.global.crypto.EncryptionProperties
import org.springframework.stereotype.Component
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 소셜 신원을 쿼터 주체 키로 바꾼다. 토큰 한도를 회원이 아니라 사람에게 귀속시키는 값이다(#222).
 *
 * `HMAC-SHA256(indexKey, "<provider>:<providerId>")` 의 hex 다. `provider_id` 자체가 개인
 * 식별정보인데 이 값은 탈퇴 후에도 쿼터 행에 남아야 해서 해시로 둔다.
 *
 * [EncryptionProperties.decodedIndexKey] 를 재사용한다 - 인덱스 키는 이미 "암호화 키와 분리해 둔
 * 동등성 비교 전용 keyed hash 키"로 용도가 같다. 대가는 이 키를 교체하면 검색 인덱스와 함께 주체
 * 키도 무효가 되어 이월이 한 구간 끊기는 것이다.
 *
 * 제공자가 다르면 주체가 다르다. 두 계정이 같은 사람이라는 근거가 서버에 없어서 그렇게 둔다.
 */
@Component
class SubjectKeyGenerator(
    properties: EncryptionProperties,
) {
    private val key = SecretKeySpec(properties.decodedIndexKey, ALGORITHM)

    /**
     * 구분자를 두는 이유는 경계가 없으면 서로 다른 신원이 같은 입력이 되기 때문이다 -
     * `("GOOGLEX", "1")` 과 `("GOOGLE", "X1")` 이 구별되지 않는다.
     */
    fun generate(
        provider: String,
        providerId: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return HEX.formatHex(mac.doFinal("$provider$SEPARATOR$providerId".toByteArray()))
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"

        const val SEPARATOR = ":"

        /** 컬럼이 CHAR(64) 라 소문자 hex 64자와 맞아야 한다(V38). */
        val HEX: HexFormat = HexFormat.of()
    }
}
