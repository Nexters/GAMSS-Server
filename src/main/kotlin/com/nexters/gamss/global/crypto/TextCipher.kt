package com.nexters.gamss.global.crypto

import org.springframework.stereotype.Component
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 대화 텍스트를 AES-256-GCM으로 암·복호화한다. 저장 형식은 `enc:{키버전}:{Base64(IV‖암호문‖태그)}` 이고,
 * IV는 값마다 새로 뽑는다 — 같은 평문이라도 매번 다른 암호문이 되어야 덤프에서 "같은 내용"이 드러나지 않는다.
 *
 * **프리픽스가 없는 값은 평문으로 간주해 그대로 돌려준다.** 암호화 도입 이전에 저장된 행이 남아 있고
 * (출시 전이라 백필하지 않는다), 테스트 픽스처도 SQL로 평문을 직접 넣기 때문이다. 쓰기는 항상
 * 암호화하므로 이 관용은 읽기에만 있다. 대신 "프리픽스 없는 값 = 평문"이라는 규칙이 남으므로,
 * 나중에 평문을 아예 금지하려면 그때 백필과 함께 이 분기를 없애야 한다.
 *
 * **AAD 를 쓰지 않으므로 암호문을 다른 행으로 옮겨 붙이는 변조는 탐지하지 못한다.** 인증 태그는
 * "이 키로 만들어졌다"만 보증하고 "이 행의 이 컬럼에 속한다"는 보증하지 않는다. 행 단위로 묶으려면
 * 컨버터가 식별자를 알아야 해서 값만 받는 [jakarta.persistence.AttributeConverter] 구조를 버려야 한다.
 * 이 위협(DB 쓰기 권한 침해)은 저장 매체 유출을 막으려는 이 설계의 범위 밖이다.
 *
 * 반대로 **프리픽스가 있는데 복호화에 실패하면 예외를 던진다.** 여기서 평문처럼 흘려보내면 키를 잘못
 * 주입한 배포가 조용히 암호문을 화면에 뿌린다 — 침묵보다 즉시 실패가 낫다. 실패 원인이 형식 오류든
 * 키 불일치든 전부 `IllegalStateException` 하나로 던진다 — 호출부가 구분해서 처리할 방법이 없고,
 * 어느 쪽이든 사람이 봐야 하는 상황이기 때문이다.
 */
@Component
class TextCipher(
    properties: EncryptionProperties,
) {
    private val key = SecretKeySpec(properties.decodedDataKey, KEY_ALGORITHM)
    private val keyVersion = properties.keyVersion
    private val prefix = "$MARKER${properties.keyVersion}:"
    private val random = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
        // Cipher는 스레드 안전하지 않아 호출마다 새로 만든다(공유하면 동시 요청이 서로의 상태를 덮는다).
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return prefix + Base64.getEncoder().encodeToString(iv + cipher.doFinal(plaintext.toByteArray()))
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(MARKER)) {
            return stored
        }
        val version = stored.removePrefix(MARKER).substringBefore(':')
        check(stored.startsWith(prefix)) { "알 수 없는 암호화 키 버전입니다: $version(현재 $keyVersion)" }

        // Base64 파싱 실패도 "이 값은 복호화할 수 없다"는 같은 뜻이다. 아래 인증 실패와 예외 타입이
        // 갈리면 호출부가 두 가지를 따로 잡아야 하므로 여기서 묶는다.
        val decoded =
            try {
                Base64.getDecoder().decode(stored.removePrefix(prefix))
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("암호문이 Base64 형식이 아닙니다(데이터 훼손).", e)
            }
        check(decoded.size > IV_SIZE_BYTES) { "암호문 길이가 IV보다 짧습니다(데이터 훼손)." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, decoded, 0, IV_SIZE_BYTES))
        return try {
            String(cipher.doFinal(decoded, IV_SIZE_BYTES, decoded.size - IV_SIZE_BYTES))
        } catch (e: GeneralSecurityException) {
            // 인증 태그 불일치 = 키가 다르거나 저장된 값이 훼손됐다는 뜻이다. 어느 쪽이든 복구 불가라 즉시 실패시킨다.
            throw IllegalStateException("암호문을 복호화하지 못했습니다(키 불일치 또는 데이터 훼손).", e)
        }
    }

    companion object {
        /** 암호문임을 알리는 표식. 이 표식으로 시작하지 않는 값은 전부 평문으로 읽는다. */
        private const val MARKER = "enc:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"

        /** GCM 권장 IV 길이. 12바이트를 벗어나면 내부에서 추가 해싱이 일어나 이득 없이 느려진다. */
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
    }
}
