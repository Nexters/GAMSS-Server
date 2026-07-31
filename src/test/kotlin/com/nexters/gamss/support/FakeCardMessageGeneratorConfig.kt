package com.nexters.gamss.support

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FakeCardMessageGeneratorConfig {
    @Bean
    @Primary
    fun cardMessageGenerator(): CardMessageGenerator =
        object : CardMessageGenerator {
            override fun generate(
                emotion: EmotionType,
                summary: String,
            ): CardMessageOutput = CardMessageOutput("$emotion 카드 대사: $summary", usedTokens = 10, cachedTokens = 0)
        }
}
