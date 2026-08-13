package com.nexters.gamss.support

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.generation.EmotionExtractionOutput
import com.nexters.gamss.llm.generation.EmotionExtractor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FakeEmotionExtractorConfig {
    @Bean
    @Primary
    fun emotionExtractor(): EmotionExtractor =
        object : EmotionExtractor {
            override fun extract(userMessages: List<String>): EmotionExtractionOutput =
                EmotionExtractionOutput(EmotionType.SADNESS, usedTokens = 5, cachedTokens = 0)
        }
}
