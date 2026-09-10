package com.nexters.gamss.member.domain

/**
 * 신규 가입 시 닉네임 초기값을 정한다. [candidates] 를 순서대로 시도해 처음 성공하는 값을
 * 쓴다. 후보를 추가·재배열하려면 이 목록만 바꾸면 되고([candidates] 를 새로 조립해 넘기면
 * 된다), 각 [NicknameCandidateSource] 구현은 서로의 존재를 모른다(OCP).
 *
 * 마지막 후보([RandomNicknameSource])가 입력과 무관하게 항상 성공하도록 만들어져 있어
 * [resolve] 는 원칙적으로 항상 값을 돌려준다. 그 전제가 깨지면(후보 구성을 잘못 바꾸는 등)
 * 조용히 null 을 만드는 대신 그 자리에서 예외를 던진다.
 */
class InitialNicknameResolver(
    private val candidates: List<NicknameCandidateSource> =
        listOf(
            OriginalNameNicknameSource(),
            WhitespaceTokenNicknameSource(),
            TruncatedNameNicknameSource(),
            RandomNicknameSource(),
        ),
) {
    fun resolve(name: String?): Nickname =
        candidates.firstNotNullOfOrNull { it.suggest(name) }
            ?: error("모든 닉네임 후보가 실패했다 - 마지막 후보는 항상 성공해야 한다")
}
