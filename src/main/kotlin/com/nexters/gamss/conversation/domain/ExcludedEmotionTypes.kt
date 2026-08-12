package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode

/**
 * 새 채팅방을 만들 때 지정한, 반응하지 않을 캐릭터 목록 값 객체. 중복 제거·최대 개수(전체 종 수 - 1,
 * 즉 후보가 최소 1종은 남아야 함) 불변식을 스스로 보장하고, DB 저장용 콤마구분 문자열 직렬화/역직렬화도
 * 여기서 담당한다([Conversation]은 원시 컬럼만 들고 이 클래스에 위임).
 */
class ExcludedEmotionTypes private constructor(
    val values: List<EmotionType>,
) {
    fun toColumnValue(): String? = values.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

    fun toSet(): Set<EmotionType> = values.toSet()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ExcludedEmotionTypes) {
            return false
        }
        return values == other.values
    }

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = values.toString()

    companion object {
        val EMPTY = ExcludedEmotionTypes(emptyList())

        /**
         * 사용자 요청값을 dedup 후 검증한다. 전체 종을 다 제외하면(남는 후보가 없으면) 거부한다.
         *
         * 신규 생성 대상에서 빠진 캐릭터(WARM)는 400으로 막지 않고 조용히 걸러낸다 — 어차피
         * 뽑히지 않아 목록에 넣어도 의미가 없는 값이고, 서버보다 늦게 배포되는 구버전 앱이
         * 한동안 계속 보내오기 때문이다. 걸러내지 않으면 개수 상한 계산에 섞여 "후보가 남았는데도
         * 거부"가 된다.
         */
        fun of(emotionTypes: List<EmotionType>): ExcludedEmotionTypes {
            val distinct = emotionTypes.filter { it.selectable }.distinct()
            if (distinct.size >= EmotionType.SELECTABLE.size) {
                throw BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "excludeCharacters는 최대 ${EmotionType.SELECTABLE.size - 1}종까지만 지정할 수 있습니다.",
                )
            }
            return ExcludedEmotionTypes(distinct)
        }

        /**
         * DB 컬럼 값을 복원한다. 저장된 값은 생성 시점에 이미 검증됐으므로 재검증하지 않는다.
         * 캐릭터 교체 이전에 저장된 행에는 WARM이 남아 있을 수 있지만, 후보 풀
         * ([EmotionType.SELECTABLE])에 애초에 없어 무동작이다.
         */
        fun fromColumnValue(raw: String?): ExcludedEmotionTypes =
            if (raw.isNullOrBlank()) EMPTY else ExcludedEmotionTypes(raw.split(",").map(EmotionType::valueOf))
    }
}
