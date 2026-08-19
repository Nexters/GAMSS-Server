package com.nexters.gamss.global.crypto

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
