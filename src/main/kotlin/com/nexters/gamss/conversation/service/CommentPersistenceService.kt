package com.nexters.gamss.conversation.service

import com.nexters.gamss.conversation.domain.CommentStatus
import com.nexters.gamss.conversation.domain.Message
import com.nexters.gamss.conversation.domain.SenderType
import com.nexters.gamss.conversation.repository.ConversationRepository
import com.nexters.gamss.conversation.repository.MessageRepository
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.llm.parsing.CommentFeed
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 검증까지 끝난 [CommentFeed]를 실제로 저장한다. 별도 빈으로 분리한 이유는 [CommentGenerationService]가
 * 자기 자신을 호출(self-invocation)하면 @Transactional 프록시를 우회해 트랜잭션이 걸리지 않기 때문이다.
 */
@Service
class CommentPersistenceService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    /**
     * 1라운드(comments)를 먼저 저장해 character_id -> messageId 맵을 만들고, 그 맵으로 2라운드(tikitaka)의
     * replyTo(character_id)를 실제 repliesToMessageId(PK)로 치환해 저장한다. 저장 순서가 중요한 이유는
     * 2라운드 행의 FK 값 자체가 1라운드 INSERT 결과(생성된 PK)에 의존하기 때문이다.
     *
     * 반환 순서는 저장 순서와 다르다 — 각 티키타카를 자신이 답장한 1라운드 댓글 바로 뒤에 끼워 넣어,
     * 클라이언트가 받는 목록이 실제 대화 스레드처럼 읽히게 한다.
     */
    @Transactional
    fun saveFeed(
        conversationId: Long,
        rootMessageId: Long,
        feed: CommentFeed,
    ): List<Message> {
        val conversation =
            conversationRepository
                .findById(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }

        val characterIdToMessageId = mutableMapOf<EmotionType, Long>()
        val savedComments =
            feed.comments.map { draft ->
                val saved =
                    messageRepository.save(
                        conversation.createMessage(
                            senderType = SenderType.CHARACTER,
                            emotionType = draft.characterId,
                            content = draft.text,
                            repliesToMessageId = null,
                            rootMessageId = rootMessageId,
                        ),
                    )
                characterIdToMessageId[draft.characterId] = saved.id
                saved
            }

        val savedTikitaka =
            feed.tikitaka.map { draft ->
                messageRepository.save(
                    conversation.createMessage(
                        senderType = SenderType.CHARACTER,
                        emotionType = draft.characterId,
                        content = draft.text,
                        repliesToMessageId = characterIdToMessageId.getValue(draft.replyTo),
                        rootMessageId = rootMessageId,
                    ),
                )
            }

        val updated =
            messageRepository.updateCommentStatus(
                rootMessageId,
                CommentStatus.DONE,
                listOf(CommentStatus.PENDING),
                Instant.now(),
            )
        check(updated == 1) { "댓글 저장 중 상태 전이가 실패했습니다. rootMessageId=$rootMessageId" }

        val tikitakaByTarget = savedTikitaka.groupBy { it.repliesToMessageId }
        return savedComments.flatMap { comment -> listOf(comment) + tikitakaByTarget[comment.id].orEmpty() }
    }

    /** 유저의 답글([repliesToMessageId])에 캐릭터([characterId])가 다시 응답한 메시지 1개를 저장한다. */
    @Transactional
    fun saveReply(
        conversationId: Long,
        rootMessageId: Long,
        repliesToMessageId: Long,
        characterId: EmotionType,
        text: String,
    ): Message {
        val conversation =
            conversationRepository
                .findById(conversationId)
                .orElseThrow { BusinessException(ErrorCode.CONVERSATION_NOT_FOUND) }

        val saved =
            messageRepository.save(
                conversation.createMessage(
                    senderType = SenderType.CHARACTER,
                    emotionType = characterId,
                    content = text,
                    repliesToMessageId = repliesToMessageId,
                    rootMessageId = rootMessageId,
                ),
            )

        val updated =
            messageRepository.updateCommentStatus(
                repliesToMessageId,
                CommentStatus.DONE,
                listOf(CommentStatus.PENDING),
                Instant.now(),
            )
        check(updated == 1) { "답글 저장 중 상태 전이가 실패했습니다. repliesToMessageId=$repliesToMessageId" }

        return saved
    }
}
