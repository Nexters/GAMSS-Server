package com.nexters.gamss.game.domain

/**
 * 기록을 남기는 미니게임 종류. 게임마다 다른 규칙(경로 이름·점수 상한)은 각 상수가 직접 가진다.
 * 새 게임은 상수 하나만 추가하면 되고 서비스·컨트롤러는 그대로다.
 */
enum class GameType(
    val pathName: String,
    private val maxScore: Int,
) {
    // 점수는 제한 시간 안의 터치 횟수. 상한은 화면 표기(5자리)에 맞춘 비정상 값 방어선이다.
    CLICKER("clicker", 99_999),
    ;

    fun accepts(score: Int): Boolean = score in 0..maxScore

    companion object {
        fun fromPathName(pathName: String): GameType? = entries.find { it.pathName == pathName.lowercase() }
    }
}
