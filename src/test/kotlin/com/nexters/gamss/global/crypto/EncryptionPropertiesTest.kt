package com.nexters.gamss.global.crypto

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 키 설정 검증. 잘못 주입된 키는 부팅 시점에 걸러야 한다 — 런타임에 발견되면 이미 평문이나
 * 못 읽는 암호문이 DB에 들어간 뒤다.
 */
class EncryptionPropertiesTest {
    @Test
    fun `32바이트가 아닌 키는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionProperties(dataKey = SHORT_KEY, indexKey = INDEX_KEY)
        }
    }

    @Test
    fun `Base64가 아닌 키는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionProperties(dataKey = "이건 base64가 아니다", indexKey = INDEX_KEY)
        }
    }

    @Test
    fun `암호화 키와 인덱스 키가 같으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionProperties(dataKey = DATA_KEY, indexKey = DATA_KEY)
        }
    }

    @Test
    fun `키 버전에 콜론이 있으면 거부한다`() {
        // 암호문 프리픽스의 구분자가 ':' 라, 버전에 섞이면 파싱이 어긋난다.
        assertFailsWith<IllegalArgumentException> {
            EncryptionProperties(dataKey = DATA_KEY, indexKey = INDEX_KEY, keyVersion = "v1:1")
        }
    }

    @Test
    fun `toString에 키 원문이 들어가지 않는다`() {
        // 바인딩이 실패하면 스프링이 이 객체를 로그에 찍는다. 그때 키가 새면 로그 파일에 영구히 남는다.
        val rendered = testProperties().toString()

        assertFalse(DATA_KEY in rendered, "암호화 키가 노출되면 안 된다")
        assertFalse(INDEX_KEY in rendered, "인덱스 키가 노출되면 안 된다")
        assertTrue("v1" in rendered, "키 버전은 남겨 어느 설정인지 알 수 있어야 한다")
    }

    @Test
    fun `키 배열을 밖에서 고쳐도 원본은 그대로다`() {
        val properties = testProperties()

        properties.decodedDataKey.fill(0)

        assertFalse(properties.decodedDataKey.all { it == 0.toByte() }, "복사본을 줘야 원본이 안 망가진다")
    }
}
