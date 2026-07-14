package com.nexters.gamss

import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfig::class)
@SpringBootTest
class GamssApplicationTests {
    @Test
    fun contextLoads() {
    }
}
