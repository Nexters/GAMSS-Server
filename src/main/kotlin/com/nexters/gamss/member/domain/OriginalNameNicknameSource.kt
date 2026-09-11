package com.nexters.gamss.member.domain

/** 소셜 이름을 그대로 닉네임으로 시도한다. */
class OriginalNameNicknameSource : NicknameCandidateSource {
    override fun suggest(name: String?): Nickname? = Nickname.tryCreate(name)
}
