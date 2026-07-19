package com.nexters.gamss.conversation.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalTime

/**
 * 대화 도메인 설정. dayStartTime은 서비스상 "하루"가 시작되는 시각으로,
 * 날짜별 대화 목록 조회의 경계가 된다. (예: 06:00이면 새벽 2시 대화는 전날로 분류)
 */
@ConfigurationProperties(prefix = "conversation")
data class ConversationProperties(
    val dayStartTime: LocalTime,
)
