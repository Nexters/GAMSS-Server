package com.nexters.gamss.member.domain

/**
 * 공백으로 나눈 단어를 앞에서부터 순서대로 시도해 처음 규칙에 맞는 것을 채택한다.
 * 영문 풀네임처럼 공백 포함 원본은 상한을 넘어도, 성·이름 한 단어씩은 규칙 안에
 * 드는 경우가 많다. 이름 표기 순서(이름-성 / 성-이름)를 가리지 않기 위해 첫 단어만
 * 보지 않고 모든 단어를 순서대로 확인한다.
 */
class WhitespaceTokenNicknameSource : NicknameCandidateSource {
    override fun suggest(name: String?): Nickname? =
        name
            ?.split(WHITESPACE)
            ?.firstNotNullOfOrNull { Nickname.tryCreate(it) }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}
