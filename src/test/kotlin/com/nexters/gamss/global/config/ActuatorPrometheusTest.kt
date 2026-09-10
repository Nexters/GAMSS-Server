package com.nexters.gamss.global.config

import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 모니터링 서버가 긁어가는 메트릭 엔드포인트가 열려 있는지 고정한다.
 *
 * 이 경로는 인증 없이 열려 있고(SecurityConfig.PUBLIC_PATHS) 외부 노출은 네트워크 계층이 막는데,
 * 그래서 앱 쪽에서 조용히 닫혀도 아무도 실패하지 않는다 — 대시보드가 비어야 비로소 알게 된다.
 * 노출 설정이나 공개 경로가 되돌아가면 여기서 먼저 깨지게 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class ActuatorPrometheusTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @Test
    fun `메트릭 엔드포인트는 인증 없이 200을 반환한다`() {
        mockMvc
            .get("/actuator/prometheus")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `앱이 직접 계측한 도메인 지표가 함께 노출된다`() {
        // 계측 자체가 빠지면 엔드포인트는 200이라 위 테스트만으로는 통과한다.
        // 대시보드·알림이 이름으로 조회하므로 이름이 바뀌는 것도 여기서 잡는다.
        mockMvc
            .get("/actuator/prometheus")
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("gamss_conversation_active")) }
                content { string(org.hamcrest.Matchers.containsString("gamss_domain_metrics_last_success_timestamp_seconds")) }
            }
    }

    @Test
    fun `Gemini 호출 경로별 서킷 상태가 노출된다`() {
        // 서킷이 여닫히는 것은 로그를 뒤지지 않으면 보이지 않는다. 기동 직후부터 두 경로가 다 보여야
        // 첫 장애 전에도 대시보드가 비어 있지 않다.
        mockMvc
            .get("/actuator/prometheus")
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("resilience4j_circuitbreaker_state")) }
                content { string(org.hamcrest.Matchers.containsString("gemini-ai-studio")) }
                content { string(org.hamcrest.Matchers.containsString("gemini-vertex-ai")) }
            }
    }
}
