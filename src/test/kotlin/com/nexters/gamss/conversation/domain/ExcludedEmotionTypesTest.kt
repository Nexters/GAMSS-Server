package com.nexters.gamss.conversation.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExcludedEmotionTypesTest {
    @Test
    fun `중복을 제거해서 저장한다`() {
        val excluded = ExcludedEmotionTypes.of(listOf(EmotionType.ANGER, EmotionType.ANGER, EmotionType.ANXIETY))

        assertEquals(listOf(EmotionType.ANGER, EmotionType.ANXIETY), excluded.values)
    }

    @Test
    fun `전체 종을 다 제외하면 INVALID_INPUT`() {
        val exception = assertFailsWith<BusinessException> { ExcludedEmotionTypes.of(EmotionType.SELECTABLE) }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `1종만 남기고 전부(5종) 제외할 수 있다`() {
        val excludeFive = EmotionType.SELECTABLE.drop(1)

        val excluded = ExcludedEmotionTypes.of(excludeFive)

        assertEquals(excludeFive, excluded.values)
    }

    @Test
    fun `신규 생성에서 빠진 캐릭터는 제외 목록에서 걸러낸다`() {
        // 구버전 앱이 계속 보내오는 값이라 400으로 막지 않는다.
        val excluded = ExcludedEmotionTypes.of(listOf(EmotionType.WARM, EmotionType.ANGER))

        assertEquals(listOf(EmotionType.ANGER), excluded.values)
    }

    @Test
    fun `신규 생성에서 빠진 캐릭터는 제외 개수 상한에 포함되지 않는다`() {
        // 구버전 앱이 "다정이 포함 6종"을 보내는 상황. 걸러낸 5종은 상한(5종) 안이므로 통과해야 한다.
        val excludeFivePlusWarm = EmotionType.SELECTABLE.drop(1) + EmotionType.WARM

        val excluded = ExcludedEmotionTypes.of(excludeFivePlusWarm)

        assertEquals(EmotionType.SELECTABLE.drop(1), excluded.values)
    }

    @Test
    fun `빈 목록이면 컬럼 값이 null이다`() {
        assertEquals(null, ExcludedEmotionTypes.EMPTY.toColumnValue())
    }

    @Test
    fun `콤마구분 컬럼 값으로 직렬화한다`() {
        val excluded = ExcludedEmotionTypes.of(listOf(EmotionType.ANGER, EmotionType.ANXIETY))

        assertEquals("ANGER,ANXIETY", excluded.toColumnValue())
    }

    @Test
    fun `컬럼 값을 그대로 복원한다`() {
        val restored = ExcludedEmotionTypes.fromColumnValue("ANGER,ANXIETY")

        assertEquals(listOf(EmotionType.ANGER, EmotionType.ANXIETY), restored.values)
    }

    @Test
    fun `null이나 빈 컬럼 값은 빈 목록으로 복원한다`() {
        assertEquals(ExcludedEmotionTypes.EMPTY, ExcludedEmotionTypes.fromColumnValue(null))
        assertEquals(ExcludedEmotionTypes.EMPTY, ExcludedEmotionTypes.fromColumnValue(""))
    }

    @Test
    fun `toSet은 중복 없는 집합을 반환한다`() {
        val excluded = ExcludedEmotionTypes.of(listOf(EmotionType.ANGER, EmotionType.ANXIETY))

        assertEquals(setOf(EmotionType.ANGER, EmotionType.ANXIETY), excluded.toSet())
    }

    @Test
    fun `같은 값을 가지면 동등하다`() {
        val a = ExcludedEmotionTypes.of(listOf(EmotionType.ANGER, EmotionType.ANXIETY))
        val b = ExcludedEmotionTypes.of(listOf(EmotionType.ANGER, EmotionType.ANXIETY))

        assertTrue(a == b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
