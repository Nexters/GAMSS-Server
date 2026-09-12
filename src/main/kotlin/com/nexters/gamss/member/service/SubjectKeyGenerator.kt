package com.nexters.gamss.member.service

import com.nexters.gamss.global.crypto.EncryptionProperties
import org.springframework.stereotype.Component
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 소셜 신원을 **쿼터 주체 키**로 바꾼다. 토큰 한도를 회원이 아니라 사람에게 귀속시키는 값이다(#222).
 *
 * `HMAC-SHA256(indexKey, "<provider>:<providerId>")` 의 hex 다. 원문을 그대로 저장하지 않는 이유는
 * `provider_id` 자체가 개인 식별정보이고, 이 값은 탈퇴 후에도 한 구간 남아야 하기 때문이다
 * ([com.nexters.gamss.member.domain.MemberSocialIdentity]). 해시라 키 없이는 역산할 수 없다.
 *
 * **[EncryptionProperties.decodedIndexKey] 를 쓴다.** 새 키를 만들지 않은 것은 용도가 같기 때문이다 -
 * 인덱스 키는 이미 "암호화 키와 분리해 둔, 동등성 비교 전용 keyed hash 키"다
 * ([com.nexters.gamss.global.crypto.BlindIndexer] 와 같은 역할). 대신 대가가 있다: 이 키를 교체하면
 * 검색 인덱스와 함께 이미 저장된 주체 키도 전부 무효가 되어, 재가입 이월이 한 구간 동안 끊긴다.
 *
 * 제공자가 다르면 주체가 다르다. 구글로 한도를 쓰고 애플로 가입하면 이어지지 않는다 - 두 계정이 같은
 * 사람이라는 근거가 서버에 없어서 그렇게 둔 것이고, 이 기능이 올리는 것은 "탈퇴 버튼"에서 "새 소셜
 * 계정 생성"까지다.
 */
@Component
class SubjectKeyGenerator(
    properties: EncryptionProperties,
) {
    private val key = SecretKeySpec(properties.decodedIndexKey, ALGORITHM)

    /**
     * [provider] 와 [providerId] 를 구분자로 이어 해시한다.
     *
     * 구분자를 두는 이유는 경계가 없으면 서로 다른 신원이 같은 입력이 되기 때문이다 -
     * `("GOOGLEX", "1")` 과 `("GOOGLE", "X1")` 이 이어 붙으면 구별되지 않는다. `provider` 는 enum
     * 이름이라 `:` 를 포함할 수 없어 구분자로 안전하다.
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
