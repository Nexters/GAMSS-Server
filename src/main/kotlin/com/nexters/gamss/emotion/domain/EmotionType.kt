package com.nexters.gamss.emotion.domain

/**
 * 감정 캐릭터. 캐릭터 메시지가 어떤 감정의 말인지 나타낸다.
 *
 * 신규 생성 대상에서 빠진 캐릭터([selectable] = false)도 상수를 지우지 않는다 — 카드·메시지가
 * 이 이름을 문자열로 저장하므로(`@Enumerated(EnumType.STRING)`) 상수를 없애면 과거 레코드를 읽는
 * 순간 역직렬화가 깨진다. 그래서 "저장된 값으로는 계속 유효하되 새로 뽑히지는 않는" 상태를
 * [selectable]로 구분한다.
 */
enum class EmotionType(
    val label: String,
    /** 신규 생성(댓글·카드)에서 뽑힐 수 있는지. 생성 대상에서 빠진 캐릭터만 false다. */
    val selectable: Boolean = true,
) {
    JOY("기쁨"),
    SADNESS("슬픔"),
    ANGER("분노"),
    ANXIETY("불안"),
    GRUMPY("까칠"),
    QUIRKY("엉뚱"),

    /** 슬픔이로 교체돼 신규 생성 대상에서 빠졌다. 과거 레코드에만 남고, 그 댓글에 달리는 답글 생성에는 계속 쓰인다. */
    WARM("다정", selectable = false),
    ;

    companion object {
        /** 신규 생성 후보 전체. 캐릭터 선택·제외 검증은 전부 이 집합을 기준으로 한다. */
        val SELECTABLE: List<EmotionType> = entries.filter { it.selectable }
    }
}
