package com.nexters.gamss.member.domain

import kotlin.random.Random

/**
 * 앞선 후보가 모두 실패했을 때 쓰는 마지막 후보. 형용사·동물 조합은 전부 2~10 그래핌·금칙어
 * 규칙을 만족하도록 미리 골라 뒀으므로 [name] 과 무관하게 항상 성공한다. 규칙을 어기는 조합이
 * 섞여 들어가면 [Nickname] 생성자가 그 자리에서 예외를 던져 바로 드러나게 한다 - 실패를
 * 삼키는 [Nickname.tryCreate] 를 쓰지 않는 이유다.
 */
class RandomNicknameSource(
    private val random: Random = Random.Default,
) : NicknameCandidateSource {
    override fun suggest(name: String?): Nickname = Nickname(ADJECTIVES.random(random) + ANIMALS.random(random))

    companion object {
        val ADJECTIVES =
            listOf(
                "즐거운",
                "몽글한",
                "포근한",
                "느긋한",
                "새침한",
                "졸린",
                "수줍은",
                "씩씩한",
                "다정한",
                "차분한",
                "상냥한",
                "든든한",
                "엉뚱한",
                "짜릿한",
                "뭉클한",
                "반짝이는",
                "살랑이는",
                "몰랑한",
                "따뜻한",
                "신나는",
                "조용한",
                "용감한",
                "부드러운",
                "활발한",
                "은은한",
            )
        val ANIMALS =
            listOf(
                "고양이",
                "너구리",
                "다람쥐",
                "고래",
                "펭귄",
                "토끼",
                "여우",
                "부엉이",
                "곰돌이",
                "수달",
                "오리",
                "사슴",
                "하마",
                "코알라",
                "판다",
                "두더지",
                "물개",
                "햄스터",
                "딱따구리",
                "반딧불이",
                "강아지",
                "고슴도치",
                "앵무새",
                "참새",
            )
    }
}
