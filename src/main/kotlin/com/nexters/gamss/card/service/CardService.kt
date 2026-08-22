package com.nexters.gamss.card.service

import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.CardSummary
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.conversation.domain.CardGenerationStatus
import com.nexters.gamss.conversation.domain.Conversation
import com.nexters.gamss.conversation.domain.ConversationStatus
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.error.CardGenerationFailedException
import com.nexters.gamss.llm.generation.CardMessageGenerator
import com.nexters.gamss.llm.generation.CardMessageOutput
import com.nexters.gamss.llm.generation.EmotionExtractor
import com.nexters.gamss.llm.generation.LlmRetryExecutor
import com.nexters.gamss.llm.generation.TokenUsageAccumulator
import com.nexters.gamss.llm.prompt.CardMessageWindow
import com.nexters.gamss.monitoring.domain.GenerationType
import com.nexters.gamss.monitoring.service.GenerationLogRecorder
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 감정 카드 생성·조회. 카드는 종료된 대화 1개당 1개만 만들어지고(중복 생성 차단),
 * 캘린더 조회는 그 대화의 생성시간(KST) 기준으로 기존 대화 조회와 같은 날짜 경계를 따른다.
 */
@Service
class CardService(
    private val cardRepository: CardRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val cardMessageGenerator: CardMessageGenerator,
    private val emotionExtractor: EmotionExtractor,
    private val generationLogRecorder: GenerationLogRecorder,
    private val cardPersistenceService: CardPersistenceService,
    private val llmRetryExecutor: LlmRetryExecutor = LlmRetryExecutor(),
) {
    /**
     * 종료된 대화에 대해 그날 있었던 일 한 줄을 생성해 카드를 저장한다. 외부 LLM 호출이 DB
     * 커넥션을 오래 점유하지 않도록 트랜잭션으로 감싸지 않는다.
     *
     * [summary]는 클라이언트가 만든 대화 요약이다. 그대로 카드에 싣지 않고 LLM으로 한 번 다듬는다 —
     * 온프레미스 모델 산출물이라 문장이 투박하다(#111). 원본은 대화방에 남겨 다른 채팅방 댓글의
     * '과거 맥락'으로 계속 쓴다.
     *
     * [summary]가 비어 있으면 요약을 만들어 줄 클라이언트가 없는 자동 생성 배치다
     * ([com.nexters.gamss.card.service.DailyAutoCardScheduler]). 그때는 유저가 보낸 메시지 원문으로
     * 대신 만들고([fallbackSummary]) 대화방에는 아무것도 남기지 않는다 — LLM 입력용 요약과 대화방에
     * 저장할 값이 이 경우에만 갈린다.
     *
     * LLM을 부르기 전에 [Conversation.cardGenerationStatus]를 CAS로 선점한다([Message.commentStatus]와
     * 같은 패턴) — 동시 중복 요청은 선점에 실패해 LLM을 아예 호출하지 않고 즉시 반환된다.
     *
     * [emotion]이 null이면(클라이언트 추출 실패) 유저가 보낸 메시지들만 보고 서버가 대표 감정을
     * 분류해 채운다 — 선점 이후에 분류해야 동시 요청이 분류 LLM을 중복 호출하지 않는다.
     */
    fun createCard(
        memberId: Long,
        conversationId: Long,
        emotion: EmotionType?,
        summary: String?,
    ): Card {
        val conversation = claimForGeneration(conversationId, memberId)
        // 공백뿐인 요약은 없는 것과 같이 다룬다. LLM 입력에서만 걸러내고 대화방에는 남기면, 그 방이
        // 과거 맥락 풀(`summary is not null`)에 들어가 내용 없이 자리만 차지한다.
        val clientSummary = summary?.takeIf { it.isNotBlank() }
        // 감정 분류와 요약 폴백이 같은 재료(유저가 보낸 메시지 원문)를 쓴다. 둘 다 필요한 배치 경로가
        // 같은 조회를 두 번 하지 않도록 한 번만 읽어 나눠 쓴다.
        val userMessages = lazy { loadUserMessages(conversationId) }
        val promptSummary = clientSummary ?: fallbackSummary(conversationId, userMessages.value)
        // 유저 메시지가 하나도 없는 방어적 엣지 — 클라이언트가 만든 요약도 유저의 대화 내용이므로 그걸로 분류한다.
        val resolvedEmotion =
            emotion ?: extractEmotion(memberId, conversationId, userMessages.value.ifEmpty { listOfNotNull(clientSummary) })
        val output = generateMessage(resolvedEmotion, promptSummary, memberId, conversationId)
        // 프롬프트가 지시한 길이를 LLM이 넘길 수 있어 저장 직전에 한 번 자른다.
        val cardLine = CardSummary.normalize(output.summary)
        val card =
            Card(
                memberId = memberId,
                conversationId = conversationId,
                emotion = resolvedEmotion,
                summary = cardLine,
                // 카드에 남는 것은 한 줄뿐이지만, 클라이언트가 아직 어느 필드를 읽는지 몰라
                // 같은 값을 채운다(Card KDoc 참고).
                message = cardLine,
                conversationCreatedAt = conversation.createdAt,
            )
        // 대화방에는 클라이언트 원본 요약을 남긴다 — 다른 채팅방 댓글의 '과거 맥락'으로 쓰이는 값이라
        // 50자로 깎인 카드 문구보다 정보가 많은 쪽이 낫다. 없으면(배치 폴백) 남기지 않는다.
        return persistCard(card, conversationId, clientSummary)
    }

    /**
     * 카드를 저장하고, 저장 시점에 드러난 CAS 경합을 원인에 맞는 [BusinessException]으로 변환한다.
     *
     * 저장이 어떤 이유로 실패하든 상태는 되돌린다 — 이 자리는 CAS 선점 **이후**라 PENDING 으로
     * 두면 재시도가 재시도 가능한 503 이 아니라 409(생성 중)로 막힌다([loadUserMessages]와 같은 계약).
     */
    private fun persistCard(
        card: Card,
        conversationId: Long,
        summary: String?,
    ): Card =
        try {
            cardPersistenceService.save(card, conversationId, summary)
        } catch (e: DataIntegrityViolationException) {
            if (cardRepository.existsByConversationId(conversationId)) {
                // 카드는 이미 다른 요청이 저장을 마쳤다는 뜻이라 DONE으로 맞춰준다 — FAILED로 두면
                // 재선점 때마다 LLM을 다시 부르고도 매번 같은 유니크 제약에 걸려 낭비만 반복된다.
                markCardGenerationStatus(conversationId, CardGenerationStatus.DONE)
                throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS, e.message).apply { initCause(e) }
            }
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
            throw e
        } catch (e: CardGenerationStateConflictException) {
            // updated == 0이 나온 시점엔 이미 PENDING이 아니라는 뜻이라 markCardGenerationStatus로
            // 되돌릴 대상 자체가 없다 — 채팅방 삭제(status <> DELETED 조건 탈락) 아니면 정리
            // 스케줄러가 이미 PENDING을 NONE으로 되돌린 상태다.
            val conversation = conversationRepository.findById(conversationId).orElse(null)
            if (conversation?.status == ConversationStatus.DELETED) {
                throw BusinessException(ErrorCode.CONVERSATION_ALREADY_DELETED).apply { initCause(e) }
            }
            throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
        } catch (e: Exception) {
            // 커넥션 끊김·쿼리 타임아웃처럼 위 둘이 아닌 실패다. 상태만 되돌리고 예외는 변환하지 않고
            // 그대로 올린다 — 예상 밖 결함을 업무 오류로 위장하지 않는다([failureHandler]와 같은 계약).
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
            throw e
        }

    /**
     * 소유권·종료 상태를 확인한 뒤 [CardGenerationStatus]를 CAS로 선점한다.
     *
     * **일일 토큰 상한을 보지 않는다.** 카드는 대화 1개당 1장이라 반복 소비가 불가능하고,
     * 여기서 막으면 대화는 이미 종료(커밋)된 뒤라 "종료됐는데 카드 없는" 방이 남는다 — 그 방은
     * 미완성 목록에서도 카드 캘린더에서도 빠져 사용자가 재시도할 방법조차 없다.
     */
    private fun claimForGeneration(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation = getOwnedConversation(conversationId, memberId)
        // 종료 후 삭제된 방은 status가 DELETED로 덮어써져 ENDED 여부가 사라지므로, 삭제 여부를 먼저
        // 확인해야 "종료되지 않았다"는 정반대 안내가 나가지 않는다.
        conversation.ensureNotDeleted()
        if (conversation.status != ConversationStatus.ENDED) {
            throw BusinessException(ErrorCode.CONVERSATION_NOT_ENDED)
        }
        val claimed =
            conversationRepository.updateCardGenerationStatus(
                conversationId,
                CardGenerationStatus.PENDING,
                // SKIPPED는 더 이상 새로 저장되지 않지만(#204) 그 값으로 굳은 기존 행은 남아 있다.
                // 빼면 그 방의 카드 생성 요청이 CAS 0건으로 떨어져 CARD_GENERATION_IN_PROGRESS라는
                // 엉뚱한 에러가 나가고, 배치도 그 행을 선점하지 못해 영영 카드를 못 받는다.
                listOf(CardGenerationStatus.NONE, CardGenerationStatus.FAILED, CardGenerationStatus.SKIPPED),
                Instant.now(),
            )
        if (claimed == 0) {
            if (cardRepository.existsByConversationId(conversationId)) {
                throw BusinessException(ErrorCode.CARD_ALREADY_EXISTS)
            }
            throw BusinessException(ErrorCode.CARD_GENERATION_IN_PROGRESS)
        }
        return conversation
    }

    /**
     * 유저가 보낸 메시지들만 보고 LLM으로 대표 감정을 분류하고 생성 로그를 남긴다.
     * 실패 시 상태를 FAILED로 되돌린 뒤 예외로 변환한다([generateMessage]와 같은 계약) —
     * 클라이언트는 분류·한 줄 생성 어느 쪽이 실패했든 CARD_GENERATION_FAILED 하나로 재시도한다.
     *
     * 재시도 대상이 아닌 예외도 **상태 복구까지는 똑같이** 받는다([failureHandler]). 예외 자체는
     * 변환하지 않고 그대로 올린다 — 예상 밖 결함을 업무 오류로 위장하지 않으려는 것이고,
     * [com.nexters.gamss.conversation.service.CommentGenerationService]도 같은 계약이다.
     */
    private fun extractEmotion(
        memberId: Long,
        conversationId: Long,
        input: List<String>,
    ): EmotionType {
        val startedAt = System.currentTimeMillis()
        val tokens = TokenUsageAccumulator()
        val handleFailure = failureHandler(GenerationType.CARD_EMOTION, startedAt, memberId, conversationId, tokens)
        val output =
            try {
                llmRetryExecutor.execute(
                    retryOn = CardGenerationFailedException::class,
                    maxAttempts = CARD_MAX_ATTEMPTS,
                    onAttemptFailure = { _, e -> tokens.addFailed(e) },
                    onNonRetryable = handleFailure,
                    onExhausted = handleFailure,
                ) { attempt ->
                    emotionExtractor.extract(input).also {
                        tokens.add(it)
                        recordCard(GenerationType.CARD_EMOTION, true, attempt, startedAt, memberId, conversationId, tokens)
                    }
                }
            } catch (e: CardGenerationFailedException) {
                throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
            }
        return output.emotion
    }

    /**
     * 감정 분류와 요약 폴백에 넣을 유저 메시지를 읽는다. 이 조회는 CAS 선점 **이후**라 실패를 그대로
     * 던지면 상태가 PENDING으로 남아, 재시도가 재시도 가능한 503이 아니라 409(생성 중)로 막힌다 —
     * 정리 스케줄러가 타임아웃시킬 때까지. LLM 실패와 같은 계약으로 FAILED까지 되돌린다.
     */
    private fun loadUserMessages(conversationId: Long): List<String> =
        try {
            messageRepository
                .findAllByConversationIdAndSenderTypeOrderByIdAsc(conversationId, SenderType.USER)
                .map { it.content }
        } catch (e: DataAccessException) {
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
            throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
        }

    /**
     * 클라이언트 요약이 없을 때 LLM 입력으로 대신 쓸 값. 유저가 보낸 메시지를 시간순으로 이어 붙인다.
     * 요약을 만들 수 있는 것은 클라이언트뿐이라 배치는 그 값을 받을 길이 없지만, 카드 한 줄을 뽑을
     * 재료 자체는 이미 이 방에 있다(#204).
     *
     * 담을 구간은 [CardMessageWindow]가 정한다 — 감정 분류와 **같은 구간**을 봐야 카드에 적힌 사건과
     * 그 카드의 감정이 하루의 다른 절반에서 나오지 않는다.
     *
     * **이 값은 대화방에 남기지 않는다.** [com.nexters.gamss.conversation.domain.Conversation.summary]는
     * 다른 채팅방 댓글의 '과거 맥락'으로 읽히는 자리인데
     * ([com.nexters.gamss.conversation.repository.ConversationRepository.findRandomPastSummaries]),
     * 풀이 최근 5개뿐이라 압축되지 않은 원문이 들어가면 정보량이 많은 요약을 풀 밖으로 밀어낸다.
     */
    private fun fallbackSummary(
        conversationId: Long,
        userMessages: List<String>,
    ): String {
        val recent = CardMessageWindow.recentAsText(userMessages)
        // 대화방은 첫 메시지 저장과 같은 트랜잭션에서만 만들어지고 메시지를 지우는 경로가 없어 여기가
        // 비는 일은 없다. 그래도 비면(공백뿐인 메시지만 있는 경우 포함) 만들 재료가 없으니, 상태를
        // 되돌려 다음 실행이 다시 보게 한다 — 대상 조회가 FAILED 재시도를 하루 한 번으로 묶으므로
        // 영구 실패라도 태우는 양은 갇혀 있다.
        if (recent.isEmpty()) {
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
            throw BusinessException(
                ErrorCode.CARD_GENERATION_FAILED,
                "카드를 만들 대화 내용이 없습니다. conversationId=$conversationId",
            )
        }
        return recent
    }

    /**
     * LLM으로 카드 한 줄을 생성하고 생성 로그를 남긴다. 실패 시 상태를 FAILED로 되돌린 뒤 예외로
     * 변환한다. 재시도 대상이 아닌 예외의 취급은 [extractEmotion]과 같다.
     */
    private fun generateMessage(
        emotion: EmotionType,
        summary: String,
        memberId: Long,
        conversationId: Long,
    ): CardMessageOutput {
        val startedAt = System.currentTimeMillis()
        val tokens = TokenUsageAccumulator()
        val handleFailure = failureHandler(GenerationType.CARD, startedAt, memberId, conversationId, tokens)
        return try {
            llmRetryExecutor.execute(
                retryOn = CardGenerationFailedException::class,
                maxAttempts = CARD_MAX_ATTEMPTS,
                onAttemptFailure = { _, e -> tokens.addFailed(e) },
                onNonRetryable = handleFailure,
                onExhausted = handleFailure,
            ) { attempt ->
                cardMessageGenerator.generate(emotion, summary).also {
                    tokens.add(it)
                    recordCard(GenerationType.CARD, true, attempt, startedAt, memberId, conversationId, tokens)
                }
            }
        } catch (e: CardGenerationFailedException) {
            throw BusinessException(ErrorCode.CARD_GENERATION_FAILED, e.message).apply { initCause(e) }
        }
    }

    /**
     * 생성이 실패로 끝날 때 할 일을 한 덩어리로 묶는다 — **실패 로그 한 줄과 FAILED 로의 상태 복구**다.
     * `record` 가 아니라 `handle` 인 것은 기록만 하지 않기 때문이다 —
     * [com.nexters.gamss.conversation.service.CommentGenerationService] 의 같은 자리는 로그만 남기고
     * 상태 복구는 바깥 catch 가 맡는다.
     *
     * 재시도를 모두 소진했을 때(`onExhausted`)와 재시도 대상이 아닌 예외로 중단할 때
     * (`onNonRetryable`)에 남길 것이 같아 한 자리에서 만든다. 두 콜백 중 하나만 넘기면 나머지 경로가
     * 조용히 빠지는데, 그러면 **실패가 집계되지 않고 상태가 PENDING으로 남아** 사용자의 재시도가
     * 재시도 가능한 503이 아니라 409(생성 중)로 막힌다 — 정리 스케줄러가 타임아웃시킬 때까지다.
     */
    private fun failureHandler(
        type: GenerationType,
        startedAt: Long,
        memberId: Long,
        conversationId: Long,
        tokens: TokenUsageAccumulator,
    ): (Int, Throwable) -> Unit =
        { attempt, error ->
            recordCard(type, false, attempt, startedAt, memberId, conversationId, tokens, error)
            markCardGenerationStatus(conversationId, CardGenerationStatus.FAILED)
        }

    /**
     * 카드 경로의 생성 로그 한 줄. 감정 분류·한 줄 생성이 [type]만 다르고 나머지가 같아 한 곳에 모은다.
     * 성공·실패 모두 [tokens]에 **그때까지 누적된 합계**를 싣는다 — 파싱에 실패한 시도도 호출은 됐으니
     * 과금되기 때문에, 마지막 한 시도만 기록하면 비용이 과소 집계된다.
     *
     * [error]는 [Throwable]로 받는다. 재시도 대상인 [CardGenerationFailedException]뿐 아니라 재시도
     * 대상이 아닌 예외로 중단될 때도 같은 자리에 원인을 남겨야 실패 집계가 새지 않기 때문이다.
     */
    private fun recordCard(
        type: GenerationType,
        success: Boolean,
        attempt: Int,
        startedAt: Long,
        memberId: Long,
        conversationId: Long,
        tokens: TokenUsageAccumulator,
        error: Throwable? = null,
    ) {
        generationLogRecorder.record(
            type = type,
            success = success,
            attemptCount = attempt,
            latencyMs = System.currentTimeMillis() - startedAt,
            memberId = memberId,
            conversationId = conversationId,
            usedTokens = tokens.used,
            cachedTokens = tokens.cached,
            inputTokens = tokens.input,
            outputTokens = tokens.output,
            failureReason = error?.let { (it.cause ?: it).javaClass.simpleName },
        )
    }

    private fun markCardGenerationStatus(
        conversationId: Long,
        status: CardGenerationStatus,
    ) {
        val updated =
            conversationRepository.updateCardGenerationStatus(
                conversationId,
                status,
                listOf(CardGenerationStatus.PENDING),
                Instant.now(),
            )
        if (updated == 0) {
            log.warn("카드 생성 상태 전이 실패: conversationId={}, to={} (이미 PENDING 상태가 아님)", conversationId, status)
        }
    }

    /**
     * 본인 카드 한 장을 id 로 조회한다.
     *
     * 지운 카드와 삭제된 채팅방의 카드는 **없는 것으로 취급한다**(CARD_NOT_FOUND) — 날짜별·월별
     * 조회에서 이미 사라진 카드라, id 로만 열리면 사용자가 보는 목록과 어긋난다. 삭제 API 가
     * CARD_ALREADY_DELETED(409)를 쓰는 것은 재호출을 구분해야 하는 변경 요청이기 때문이고,
     * 읽기에는 그 구분이 필요 없다.
     */
    @Transactional(readOnly = true)
    fun getCard(
        memberId: Long,
        cardId: Long,
    ): Card {
        val card =
            cardRepository
                .findVisibleById(cardId)
                .orElseThrow { BusinessException(ErrorCode.CARD_NOT_FOUND) }
        if (!card.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CARD_ACCESS_DENIED)
        }
        return card
    }

    /** 날짜(KST 자정~자정)에 속한 카드를 조회한다. */
    @Transactional(readOnly = true)
    fun getCardsByDate(
        memberId: Long,
        date: LocalDate,
    ): List<Card> {
        val start = date.atStartOfDay(ZONE).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        return cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(memberId, start, end)
    }

    /** 월(KST) 전체에 속한 카드를 조회한다(캘린더용). */
    @Transactional(readOnly = true)
    fun getCardsByMonth(
        memberId: Long,
        yearMonth: YearMonth,
    ): List<Card> {
        val (start, end) = monthRange(yearMonth)
        return cardRepository.findAllByMemberIdAndConversationCreatedAtInRange(memberId, start, end)
    }

    /**
     * 월(KST) 전체에서 [emotion] 카드만 최신순으로 조회한다(감정 탭에서 한 달치 몰아보기).
     *
     * 캘린더 조회([getCardsByMonth])와 같은 월 경계를 쓰되 카드 내용까지 돌려준다 — 캘린더로 날짜만
     * 받아 [getCardsByDate] 를 날짜마다 다시 부르는 N+1 호출을 없애려고 만든 조회다.
     *
     * 페이지네이션이 없다. 한 달·한 감정이면 사용자가 그 달에 만든 채팅방 수를 넘지 못해 페이징의
     * 이득보다 복잡도가 크다 — 한 사람이 한 달에 만드는 카드 수가 크게 늘면 이 전제가 깨진다.
     */
    @Transactional(readOnly = true)
    fun getCardsByMonthAndEmotion(
        memberId: Long,
        yearMonth: YearMonth,
        emotion: EmotionType,
    ): List<Card> {
        val (start, end) = monthRange(yearMonth)
        return cardRepository.findAllByMemberIdAndEmotionAndConversationCreatedAtInRange(memberId, emotion, start, end)
    }

    /**
     * 월의 KST 자정~자정 경계 `[start, end)`.
     *
     * 월별 조회 둘이 같은 경계를 보도록 한 곳에 둔다 — 각자 계산하면 한쪽만 고쳤을 때 캘린더에는
     * 있는 카드가 감정 탭에서는 빠지는 식으로 갈라진다.
     */
    private fun monthRange(yearMonth: YearMonth): Pair<Instant, Instant> {
        val firstDay = yearMonth.atDay(1)
        val nextMonthFirstDay = yearMonth.plusMonths(1).atDay(1)
        return firstDay.atStartOfDay(ZONE).toInstant() to nextMonthFirstDay.atStartOfDay(ZONE).toInstant()
    }

    /**
     * 본인 카드를 삭제한다(soft delete). **카드가 나온 채팅방도 함께 삭제한다.**
     * 되돌릴 수 없다 — 카드 생성 상태가 DONE 으로 남아 같은 대화방에 카드를 다시 만들 수 없다.
     *
     * 백오피스 지표는 생성 이력이라 이 삭제로 변하지 않는다(사용자 조회에서만 감춰진다).
     *
     * 행을 잠그고 읽는다([CardRepository.findByIdForUpdate]) — 동시 삭제 요청이 같은 카드를
     * 각자 '아직 안 지워짐' 으로 읽어 둘 다 성공하는 것을 막는다.
     */
    @Transactional
    fun deleteCard(
        memberId: Long,
        cardId: Long,
    ) {
        val card =
            cardRepository
                .findByIdForUpdate(cardId)
                .orElseThrow { BusinessException(ErrorCode.CARD_NOT_FOUND) }
        if (!card.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CARD_ACCESS_DENIED)
        }
        card.delete()
        deleteConversationOf(card)
    }

    /**
     * 카드가 나온 채팅방을 함께 삭제한다(soft delete). 카드는 그 대화의 결과물이라, 카드만 지우고
     * 대화를 남기면 사용자가 지웠다고 여긴 내용이 채팅방 목록·검색에 그대로 남는다.
     *
     * 이미 삭제된 방이면 넘어간다 — 채팅방을 먼저 지운 뒤 카드를 지우는 순서에서도 카드 삭제는
     * 성공해야 한다([Conversation.delete] 는 이미 삭제된 방에 예외를 던진다).
     */
    private fun deleteConversationOf(card: Card) {
        val conversation =
            conversationRepository
                .findByIdForUpdate(card.conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (conversation.isDeleted()) {
            return
        }
        conversation.delete()
    }

    /**
     * 본인의 특정 감정 카드를 한 번에 삭제하고 삭제 건수를 돌려준다. 단건 삭제와 마찬가지로
     * 카드가 나온 대화방도 함께 삭제한다. 대상이 없어도 0 을 돌려주고 성공한다 — 연속 호출이
     * 안전해야 한다.
     */
    @Transactional
    fun deleteCardsByEmotion(
        memberId: Long,
        emotion: EmotionType,
    ): Int = deleteCardsWithConversations(cardRepository.findDeletableConversationIdsByEmotion(memberId, emotion))

    /**
     * 확정된 대화방 집합의 카드와 대화방을 함께 삭제한다(soft delete).
     *
     * 대상을 id 로 먼저 확정해두고 두 UPDATE 를 날린다 — 카드를 먼저 지우면 `deletedAt is null` 이
     * 깨져 대화방을 못 찾고, 대화방을 먼저 지우면 `status <> DELETED` 가 깨져 카드를 못 찾는다
     * ([CardRepository.findDeletableConversationIdsByEmotion] ·
     * [CardRepository.findDeletableConversationIds]).
     *
     * 감정별 삭제([deleteCardsByEmotion])와 전체 삭제([deleteAllCards])가 공유한다. **넘어오는 id 는
     * 이미 소유권으로 걸러져 있어야 한다** — 여기서는 memberId 를 다시 확인하지 않는다.
     *
     * 단건 삭제와 달리 행을 잠그지 않는다 — `deletedAt is null` 조건을 건 UPDATE 라 동시 요청이
     * 와도 뒤늦은 쪽이 0건을 갱신하고 끝난다(중복 삭제가 발생하지 않는다).
     *
     * 카드를 먼저 지우는 순서는 단건 삭제([deleteCard])와 맞춘 것이다 — 두 경로가 서로 반대
     * 순서로 행을 잡으면 동시에 들어온 요청이 상대가 잡은 행을 기다리다 데드락으로 죽는다.
     */
    private fun deleteCardsWithConversations(conversationIds: List<Long>): Int {
        if (conversationIds.isEmpty()) {
            return 0
        }
        // 카드와 대화방에 같은 시각을 찍는다 — 한 번의 삭제로 사라진 짝이라 나중에 이력을 볼 때
        // 두 UPDATE 사이의 미세한 시차로 다른 요청처럼 보이지 않아야 한다.
        val now = Instant.now()
        val deletedCards = cardRepository.softDeleteByConversationIds(conversationIds, now)
        conversationRepository.softDeleteByIds(conversationIds, now)
        return deletedCards
    }

    /**
     * 본인 카드를 한 번에 전부 삭제하고 삭제 건수를 돌려준다. 단건·감정별 삭제와 마찬가지로
     * 카드가 나온 대화방도 함께 삭제한다. 대상이 없어도 0 을 돌려주고 성공한다.
     */
    @Transactional
    fun deleteAllCards(memberId: Long): Int = deleteCardsWithConversations(cardRepository.findDeletableConversationIds(memberId))

    private fun getOwnedConversation(
        conversationId: Long,
        memberId: Long,
    ): Conversation {
        val conversation =
            conversationRepository
                .findById(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }
        if (!conversation.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CONVERSATION_ACCESS_DENIED)
        }
        return conversation
    }

    companion object {
        /**
         * 카드 경로는 아직 재시도하지 않는다. 한 요청이 감정 분류·한 줄 생성으로 LLM을 두 번 순차
         * 호출하는 구간이라, 시도 횟수를 늘리려면 nginx `proxy_read_timeout`까지 다시 계산해야 한다(#162).
         */
        private const val CARD_MAX_ATTEMPTS = 1

        private val ZONE = ZoneId.of("Asia/Seoul")
        private val log = LoggerFactory.getLogger(CardService::class.java)
    }
}
