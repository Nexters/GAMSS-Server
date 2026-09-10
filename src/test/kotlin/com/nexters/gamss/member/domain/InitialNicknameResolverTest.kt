package com.nexters.gamss.member.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InitialNicknameResolverTest {
    private val resolver = InitialNicknameResolver()

    @Test
    fun `이름이 규칙에 맞으면 원본을 그대로 쓴다`() {
        assertEquals(Nickname("홍길동"), resolver.resolve("홍길동"))
    }

    @Test
    fun `원본이 상한을 넘는 영문 풀네임이면 첫 단어를 쓴다`() {
        assertEquals(Nickname("John"), resolver.resolve("John Alexander Smith"))
    }

    @Test
    fun `공백 없이 상한을 넘으면 잘라서 쓴다`() {
        val name = "가".repeat(Nickname.MAX_LENGTH + 5)

        assertEquals(Nickname("가".repeat(Nickname.MAX_LENGTH)), resolver.resolve(name))
    }

    @Test
    fun `그 무엇도 안 되면 무작위 닉네임을 쓴다`() {
        val nickname = resolver.resolve("가")

        assertTrue(GraphemeText.count(nickname.value) in Nickname.MIN_LENGTH..Nickname.MAX_LENGTH)
    }

    @Test
    fun `이름이 없어도 무작위 닉네임을 쓴다`() {
        assertTrue(resolver.resolve(null).value.isNotBlank())
    }

    @Test
    fun `후보가 모두 실패하면 예외를 던진다`() {
        val resolver = InitialNicknameResolver(candidates = listOf(NicknameCandidateSource { null }))

        assertFailsWith<IllegalStateException> { resolver.resolve("아무거나") }
    }
}
