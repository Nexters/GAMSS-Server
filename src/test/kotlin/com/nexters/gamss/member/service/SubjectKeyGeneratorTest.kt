package com.nexters.gamss.member.service

import com.nexters.gamss.global.crypto.EncryptionProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 주체 키가 흔들리면 재가입 이월이 조용히 깨진다. 같은 신원이 같은 키를 내놓는 것과, 다른 신원이
 * 절대 같은 키를 내놓지 않는 것 둘 다 이 기능의 전제다(#222).
 */
class SubjectKeyGeneratorTest {
    private val generator = SubjectKeyGenerator(properties(INDEX_KEY))

    @Test
    fun `같은 신원은 항상 같은 키가 된다`() {
        // 탈퇴 전과 재가입 후가 같은 키여야 사용량이 이어진다.
        assertEquals(generator.generate("GOOGLE", "sub-1"), generator.generate("GOOGLE", "sub-1"))
    }

    @Test
    fun `provider 가 다르면 다른 키가 된다`() {
        assertNotEquals(generator.generate("GOOGLE", "sub-1"), generator.generate("APPLE", "sub-1"))
    }

    @Test
    fun `providerId 가 다르면 다른 키가 된다`() {
        assertNotEquals(generator.generate("GOOGLE", "sub-1"), generator.generate("GOOGLE", "sub-2"))
    }

    /**
     * 구분자가 없으면 두 신원이 같은 입력으로 뭉개진다. 남의 사용량을 물려받는 경로라 값싸게 막는다.
     */
    @Test
    fun `경계가 다른 신원이 같은 키가 되지 않는다`() {
        assertNotEquals(generator.generate("GOOGLEX", "1"), generator.generate("GOOGLE", "X1"))
    }

    @Test
    fun `CHAR 64 컬럼에 맞는 소문자 hex 다`() {
        val key = generator.generate("GOOGLE", "sub-1")

        assertEquals(64, key.length)
        assertEquals(key.lowercase(), key)
    }

    /** 인덱스 키가 바뀌면 같은 신원도 다른 주체가 된다. 키 교체의 대가를 문서가 아니라 여기서 고정한다. */
    @Test
    fun `인덱스 키가 다르면 같은 신원도 다른 키가 된다`() {
        val other = SubjectKeyGenerator(properties(OTHER_INDEX_KEY))

        assertNotEquals(generator.generate("GOOGLE", "sub-1"), other.generate("GOOGLE", "sub-1"))
    }

    private fun properties(indexKey: String) = EncryptionProperties(dataKey = DATA_KEY, indexKey = indexKey)

    private companion object {
        const val DATA_KEY = "Z2Ftc3MtdGVzdC1kYXRhLWVuY3J5cHRpb24ta2V5ISE="
        const val INDEX_KEY = "Z2Ftc3MtdGVzdC1zZWFyY2gtaW5kZXgta2V5ISEhISE="
        const val OTHER_INDEX_KEY = "Z2Ftc3MtdGVzdC1vdGhlci1pbmRleC1rZXktISEhISE="
    }
}
