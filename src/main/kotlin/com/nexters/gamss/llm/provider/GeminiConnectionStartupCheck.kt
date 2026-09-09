package com.nexters.gamss.llm.provider

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 기동 직후, 지금 쓰는 호출 경로가 실제로 쓸 수 있는 상태인지 한 번 확인한다.
 *
 * 경로가 둘로 갈리면서 두 인증 값 모두 기본값이 빈 문자열이 됐다. 예전에는 `gemini.api-key` 가
 * 필수라 값이 없으면 바인딩 단계에서 기동이 실패했지만, 지금은 아무것도 안 넣어도 앱이 뜬다.
 * 그대로 두면 키를 빼먹은 배포가 헬스체크를 통과해 자동 롤백도 걸리지 않고, 그날 새벽 배치에서야
 * 카드가 통째로 비는 것으로 드러난다.
 *
 * **기동을 막지는 않는다.** 경로가 둘인데 한쪽만 채운 환경이 정상이고([GeminiConnection] 구현체는
 * 자기 설정만 본다), 지금 안 쓰는 경로의 값이 없다고 배포가 실패하면 오히려 되돌릴 길이 막힌다.
 * 그래서 [PushConfig][com.nexters.gamss.notification.push.PushConfig] 처럼 기동을 세우는 대신
 * 에러 로그로 남긴다 - 배포 직후 로그에서 바로 보이고, 무엇이 빠졌는지까지 메시지에 담긴다.
 */
@Component
class GeminiConnectionStartupCheck(
    private val connections: GeminiConnectionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun verifyActiveConnection() {
        // 경로 조회(DB)까지 함께 감싼다. 여기서 예외가 나가면 기동이 실패하는데, 이 점검은
        // 알려주는 것이 일이지 기동을 좌우할 일이 아니다.
        runCatching {
            val connection = connections.active()
            connection.ensureUsable()
            connection
        }.onSuccess { log.info("Gemini 호출 경로 확인: {} 사용 가능", it.provider) }
            .onFailure { log.error("Gemini 호출 경로를 쓸 수 없다. 이 상태로는 생성이 전부 실패한다", it) }
    }
}
