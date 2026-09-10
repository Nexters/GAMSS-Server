package com.nexters.gamss.member.domain

/**
 * 원본 이름을 그래핌 상한까지 잘라 시도한다. 공백이 없어 단어 단위로 못 나누는
 * 이름(예: 띄어쓰기 없이 길게 이어지는 이름)을 위한, 원본 기반 마지막 후보다.
 */
class TruncatedNameNicknameSource : NicknameCandidateSource {
    override fun suggest(name: String?): Nickname? =
        name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { GraphemeText.take(it, Nickname.MAX_LENGTH) }
            ?.let { Nickname.tryCreate(it) }
}
