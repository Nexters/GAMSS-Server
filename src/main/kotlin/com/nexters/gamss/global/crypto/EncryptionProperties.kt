package com.nexters.gamss.global.crypto

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Base64

/**
 * 대화 텍스트 암호화에 쓰는 키 묶음. 두 키 모두 Base64로 인코딩한 32바이트(AES-256) 값이다.
 *
 * **[dataKey]와 [indexKey]는 반드시 다른 값이어야 한다.** 검색용 블라인드 인덱스는 평문의 지문을
 * 남기므로 암호문보다 약한 자산이다 — 같은 키를 돌려 쓰면 인덱스 키가 새는 순간 암호문까지 열린다.
 * 분리해 두면 인덱스 키 유출은 "자주 쓰는 조합 추정"에서 멈춘다.
 *
 * [keyVersion]은 암호문 앞에 붙는 프리픽스다. 나중에 키를 교체할 때 옛 버전은 옛 키로 읽고 새로
 * 쓰는 값만 새 키로 넣기 위한 자리이며, 교체 절차 자체는 아직 없다.
 */
@ConfigurationProperties(prefix = "encryption")
data class EncryptionProperties(
    val dataKey: String,
    val indexKey: String,
    val keyVersion: String = "v1",
) {
    private val decodedData: ByteArray = decode(dataKey, "encryption.data-key")

    private val decodedIndex: ByteArray = decode(indexKey, "encryption.index-key")

    init {
        require(decodedData.size == KEY_SIZE_BYTES) { "encryption.data-key는 Base64로 인코딩한 ${KEY_SIZE_BYTES}바이트여야 합니다." }
        require(decodedIndex.size == KEY_SIZE_BYTES) { "encryption.index-key는 Base64로 인코딩한 ${KEY_SIZE_BYTES}바이트여야 합니다." }
        require(!decodedData.contentEquals(decodedIndex)) { "encryption.data-key와 encryption.index-key는 서로 달라야 합니다." }
        // 프리픽스 구분자가 ':' 라, 버전 문자열에 ':' 가 들어가면 암호문 파싱이 어긋난다.
        require(keyVersion.isNotBlank() && ':' !in keyVersion) { "encryption.key-version은 비어 있을 수 없고 ':'를 포함할 수 없습니다." }
    }

    /**
     * 호출할 때마다 복사본을 준다. 배열은 가변이라 그대로 넘기면 받은 쪽이 내용을 바꿨을 때
     * 다른 소비자의 키까지 함께 망가진다 — 그렇게 되면 원인을 찾기가 매우 어렵다.
     */
    val decodedDataKey: ByteArray
        get() = decodedData.copyOf()

    val decodedIndexKey: ByteArray
        get() = decodedIndex.copyOf()

    /**
     * `data class` 가 만들어주는 toString 은 키 원문을 그대로 찍는다. 설정 바인딩이 실패하면
     * 스프링이 이 객체를 로그에 남기므로, 한 번의 실패로 키가 로그 파일에 영구히 박힌다.
     */
    override fun toString(): String = "EncryptionProperties(keyVersion=$keyVersion)"

    companion object {
        private const val KEY_SIZE_BYTES = 32

        private fun decode(
            value: String,
            name: String,
        ): ByteArray =
            try {
                Base64.getDecoder().decode(value)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$name 은 Base64 문자열이어야 합니다.", e)
            }
    }
}
