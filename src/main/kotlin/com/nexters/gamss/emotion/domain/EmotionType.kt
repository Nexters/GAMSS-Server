package com.nexters.gamss.emotion.domain

/**
 * 감정 캐릭터 6종. 캐릭터 메시지가 어떤 감정의 말인지 나타낸다.
 */
enum class EmotionType(
    val label: String,
) {
    JOY("기쁨"),
    ANGER("분노"),
    ANXIETY("불안"),
    GRUMPY("까칠"),
    WARM("다정"),
    QUIRKY("엉뚱"),
}
