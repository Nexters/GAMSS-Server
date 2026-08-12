package com.nexters.gamss.emotion.domain

/**
 * 감정 캐릭터. 캐릭터 메시지가 어떤 감정의 말인지 나타낸다.
 *
 * 카드·메시지가 이 이름을 문자열로 저장하므로(`@Enumerated(EnumType.STRING)`) 상수를 지우거나 이름을
 * 바꾸면 그 값으로 저장된 과거 레코드를 읽는 순간 역직렬화가 깨진다. 캐릭터 교체는 상수 이름이 아니라
 * 마이그레이션까지 함께 봐야 하는 변경이다.
 */
enum class EmotionType(
    val label: String,
) {
    JOY("기쁨"),
    SADNESS("슬픔"),
    ANGER("분노"),
    ANXIETY("불안"),
    GRUMPY("까칠"),
    QUIRKY("엉뚱"),
}
