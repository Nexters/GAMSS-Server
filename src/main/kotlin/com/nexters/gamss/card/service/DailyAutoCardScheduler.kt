package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.service.ConversationService
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.member.service.MemberService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 사용자가 종료 버튼을 누르지 않아 아직 열려 있는 어제까지의 대화방을 매일 새벽 자동으로 종료하고
 * 카드를 만든다. 종료 버튼을 안 눌렀다는 이유로 그날 기록이 카드로 남지 않는 것을 막는다.
 *
 * 대화방 하나의 실패가 나머지를 막지 않도록 방 단위로 예외를 삼키고, 처리 결과만 집계해 남긴다.
 * 중복 실행·재시도에 대한 멱등성은 이 클래스가 아니라 카드 생성 경로가 보장한다 — CAS 선점과
 * `cards.conversation_id` 유니크 제약에 걸린 요청은 여기서 "이미 처리됨"으로 분류된다.
 *
 * 요약([com.nexters.gamss.conversation.domain.Conversation.summary])이 없는 방은 카드 대사를 만들
 * 근거가 없으므로 종료만 하고 카드는 건너뛴다 — 요약은 프론트가 메시지마다 보내주는 값이라
 * 한 번도 보내지 않은 방에서만 생기는 경우다.
 *
 * 단, 요약 저장이 배포되기 **전에** 만들어진 방은 예외 없이 요약이 없어 전부 이 경우에 해당하므로,
 * 대상 자체를 [CardProperties.autoCardStartDate] 이후로 제한한다 — 그러지 않으면 첫 실행이 기존
 * 사용자들의 진행 중인 방을 전부 카드 없이 닫아버린다.
 */
@Component
class DailyAutoCardScheduler(
    private val conversationRepository: ConversationRepository,
    private val conversationService: ConversationService,
    private val memberService: MemberService,
    private val cardService: CardService,
    private val properties: CardProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = CRON, zone = ZONE_ID)
    fun autoEndAndCreateCards() {
        val zone = ZoneId.of(ZONE_ID)
        runFor(
            createdAfter = properties.autoCardStartDate.atStartOfDay(zone).toInstant(),
            createdBefore = LocalDate.now(zone).atStartOfDay(zone).toInstant(),
        )
    }

    /**
     * [createdAfter]와 [createdBefore] 사이에 만들어진 대상들을 처리한다. 스케줄 진입점과 분리해
     * 둔 것은 테스트가 기준 시각을 직접 주기 위해서다.
     */
    fun runFor(
        createdAfter: Instant,
        createdBefore: Instant,
    ) {
        val targetIds = conversationRepository.findAutoCardTargetIds(createdAfter, createdBefore)
        if (targetIds.isEmpty()) {
            log.info("자동 카드 생성 배치: 대상 없음 (기준={}~{})", createdAfter, createdBefore)
            return
        }
        val counts = mutableMapOf<AutoCardOutcome, Int>()
        targetIds.forEach { conversationId ->
            val outcome = process(conversationId)
            counts.merge(outcome, 1, Int::plus)
        }
        // 결과 종류가 늘어도 집계가 어긋나지 않도록 enum을 그대로 훑는다.
        log.info(
            "자동 카드 생성 배치 완료: 대상={}, {}",
            targetIds.size,
            AutoCardOutcome.entries.joinToString(", ") { "${it.label}=${counts[it] ?: 0}" },
        )
    }

    private fun process(conversationId: Long): AutoCardOutcome =
        try {
            createCardForEndedConversation(conversationId)
        } catch (e: BusinessException) {
            classify(e, conversationId)
        } catch (e: Exception) {
            // 방 하나의 예상 못 한 실패가 남은 방들을 막지 않게 한다. 카드 생성 상태는 실패 경로에서
            // 이미 FAILED로 되돌아가 있어 다음 실행이 다시 시도한다.
            log.error("자동 카드 생성 실패: conversationId={}", conversationId, e)
            AutoCardOutcome.FAILED
        }

    private fun createCardForEndedConversation(conversationId: Long): AutoCardOutcome {
        val conversation =
            conversationService.endForAutoBatch(conversationId) ?: return AutoCardOutcome.SKIPPED_DELETED
        // 탈퇴는 회원 행만 익명화하고 대화방은 남긴다. 종료까지는 상태 정리라 무해하지만, 카드는
        // 탈퇴한 사람의 대화로 만드는 새 개인 데이터라 여기서 멈춘다.
        if (memberService.getById(conversation.memberId).isWithdrawn()) {
            return AutoCardOutcome.WITHDRAWN_MEMBER
        }
        val summary = conversation.summary
        if (summary.isNullOrBlank()) {
            log.info("요약이 없어 카드 생성을 건너뛴다(종료는 완료): conversationId={}", conversationId)
            return AutoCardOutcome.NO_SUMMARY
        }
        cardService.createCard(conversation.memberId, conversationId, emotion = null, summary = summary)
        return AutoCardOutcome.CREATED
    }

    /** 배치 입장에서 정상인 실패와 진짜 실패를 가른다. */
    private fun classify(
        e: BusinessException,
        conversationId: Long,
    ): AutoCardOutcome =
        when (e.errorCode) {
            // 다른 요청이 먼저 카드를 만들었거나 만드는 중 — 배치가 할 일이 없다.
            ErrorCode.CARD_ALREADY_EXISTS, ErrorCode.CARD_GENERATION_IN_PROGRESS -> {
                AutoCardOutcome.ALREADY_HANDLED
            }

            // 한도를 배치가 대신 소진시키지 않는다. 사용자가 직접 만들 여지를 남긴다.
            ErrorCode.DAILY_TOKEN_LIMIT_EXCEEDED -> {
                AutoCardOutcome.TOKEN_LIMIT
            }

            else -> {
                log.warn("자동 카드 생성 실패: conversationId={}, errorCode={}", conversationId, e.errorCode, e)
                AutoCardOutcome.FAILED
            }
        }

    companion object {
        private const val ZONE_ID = "Asia/Seoul"

        /**
         * 매일 KST 05:00. 사용자 활동이 가장 적은 시간대라 LLM 호출이 몰려도 서비스 영향이 작다.
         *
         * 일일 토큰 리셋 시각(`token_policy.reset_hour`, 시드값 5)과 같은 시각이라 배치가 쓰는
         * 토큰은 **방금 리셋된 오늘 예산**에서 빠진다. 리셋 직전(04시 등)으로 옮기면 곧 만료될
         * 어제 예산에 잡혀 오늘 예산을 안 건드리지만, 어제 상한을 다 쓴 사용자는 스킵돼 카드를
         * 아예 못 받는다. 카드 1장은 감정 분류·대사 생성 2회로 하루 상한 대비 미미하므로 카드를
         * 확실히 만드는 쪽을 택했다.
         *
         * `reset_hour`는 백오피스에서 바꿀 수 있는 값이다 — 조정하면 위 판단의 전제가 조용히
         * 달라지므로 이 상수와 함께 봐야 한다.
         */
        private const val CRON = "0 0 5 * * *"
    }
}
