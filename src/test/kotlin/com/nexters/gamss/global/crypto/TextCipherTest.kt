package com.nexters.gamss.global.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextCipherTest {
    private val cipher = TextCipher(testProperties())

    @Test
    fun `암호화한 값을 복호화하면 원문이 나온다`() {
        val plaintext = "오늘은 회사에서 정말 힘든 일이 있었다."

        assertEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }

    @Test
    fun `암호문에는 키 버전 프리픽스가 붙는다`() {
        assertTrue(cipher.encrypt("아무 말").startsWith("enc:v1:"), "프리픽스로 암호문 여부를 판별한다")
    }

    @Test
    fun `같은 평문도 암호화할 때마다 다른 암호문이 된다`() {
        val plaintext = "같은 문장"

        // IV를 매번 새로 뽑기 때문이다. 같은 암호문이 나오면 덤프에서 "같은 내용을 쓴 사람들"이 드러난다.
        assertNotEquals(cipher.encrypt(plaintext), cipher.encrypt(plaintext))
    }

    @Test
    fun `프리픽스가 없는 값은 평문으로 그대로 읽는다`() {
        // 암호화 도입 이전에 저장된 행과 SQL로 직접 넣는 테스트 픽스처가 이 경로로 읽힌다.
        assertEquals("암호화 이전에 저장된 값", cipher.decrypt("암호화 이전에 저장된 값"))
    }

    @Test
    fun `빈 문자열도 왕복한다`() {
        assertEquals("", cipher.decrypt(cipher.encrypt("")))
    }

    @Test
    fun `다른 키로는 복호화할 수 없다`() {
        val encrypted = cipher.encrypt("비밀 이야기")
        val otherCipher = TextCipher(testProperties(dataKey = OTHER_DATA_KEY))

        assertFailsWith<IllegalStateException> { otherCipher.decrypt(encrypted) }
    }

    @Test
    fun `훼손된 암호문은 복호화에 실패한다`() {
        val encrypted = cipher.encrypt("비밀 이야기")
        val tampered = encrypted.dropLast(2) + "AA"

        // GCM 인증 태그가 잡아낸다. 조용히 넘어가면 훼손된 값이 화면까지 나간다.
        assertFailsWith<IllegalStateException> { cipher.decrypt(tampered) }
    }

    @Test
    fun `모르는 키 버전이면 복호화하지 않고 실패한다`() {
        val encrypted = TextCipher(testProperties(keyVersion = "v2")).encrypt("나중 버전으로 쓴 값")

        assertFailsWith<IllegalArgumentException> { cipher.decrypt(encrypted) }
    }
}
