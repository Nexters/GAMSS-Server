package com.nexters.gamss.conversation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 대화 도메인 설정. commentPendingTimeout은 댓글 생성이 PENDING으로 남은 채 이 시간을 넘기면
 * 고아로 간주해 NONE으로 되돌리는 기준이다(서버 크래시·배포 중단 등으로 트랜잭션이 끝까지 못 간 경우 복구용).
 */
@ConfigurationProperties(prefix = "conversation")
data class ConversationProperties(
    val commentPendingTimeout: Duration = Duration.ofMinutes(3),
)
