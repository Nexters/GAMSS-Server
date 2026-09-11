package com.nexters.gamss.member.domain

/**
 * 회원가입 시 닉네임 초기값 후보를 하나 제안한다. 규칙에 맞는 값을 못 만들면 null.
 * 새 후보 방식을 추가하려면 이 인터페이스의 구현체만 늘리면 되고, 기존 구현체나
 * [InitialNicknameResolver]는 건드리지 않는다.
 */
fun interface NicknameCandidateSource {
    fun suggest(name: String?): Nickname?
}
