package com.nexters.gamss.conversation.config

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationPropertiesTest {
    @Test
    fun `yml의 06시00분 문자열이 LocalTime으로 바인딩된다`() {
        val source = MapConfigurationPropertySource(mapOf("conversation.day-start-time" to "06:00"))

        val properties = Binder(source).bind("conversation", ConversationProperties::class.java).get()

        assertEquals(LocalTime.of(6, 0), properties.dayStartTime)
    }
}
