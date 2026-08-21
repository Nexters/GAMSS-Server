package com.nexters.gamss.card.domain

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.global.crypto.EncryptedStringConverter
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 대화 종료 시 만들어지는 감정 카드. 대화 하나를 대표하는 [emotion]과, 그날 있었던 일을 유저 시점으로
 * 적은 한 줄([summary])로 이루어진다. 회원·대화는 ID 참조([memberId]/[conversationId])로 연결한다.
 *
 * [message]에는 [summary]와 같은 값이 들어간다. 카드에 캐릭터 대사를 싣던 시절의 컬럼인데, 화면에
 * 남는 것이 한 줄뿐으로 바뀌면서 쓰임이 없어졌다(#111). 컬럼을 지우는 대신 같은 값을 채워, 아직 어느
 * 필드를 읽는지 모르는 클라이언트가 깨지지 않게 한다.
 *
 * 단 개편 이전에 저장된 행은 두 값이 다르다 — [message]에 캐릭터 대사가, [summary]에 클라이언트
 * 원본 요약이 들어 있다. 그 행들은 아래 불변식을 거치지 않았으므로(JPA 로딩은 init을 돌리지 않는다)
 * 조회는 그대로 되고, 길이·개행은 응답을 만들 때 흡수한다
 * ([com.nexters.gamss.card.controller.dto.CardResponse]).
 *
 * [summary]·[message]는 DB에 암호문으로 저장된다([com.nexters.gamss.global.crypto.EncryptedStringConverter]).
 * 날짜가 박힌 한 줄 일지라 이 컬럼만 긁어도 대화 주제가 날짜별로 재구성되기 때문이다.
 *
 * 카드가 속한 캘린더 날짜는 그 대화의 생성시간 기준이라 [conversationCreatedAt]에 비정규화해 둔다
 * — 종료 후 대화 시작 시각은 바뀌지 않으므로 날짜·월별 조회를 단일 테이블로 처리할 수 있다.
 */
@Entity
@Table(name = "cards")
class Card(
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "conversation_id", nullable = false)
    val conversationId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "emotion", length = 20, nullable = false)
    val emotion: EmotionType,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    val summary: String,
    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    val message: String,
    @Column(name = "conversation_created_at", nullable = false)
    val conversationCreatedAt: Instant,
) {
    init {
        require(summary.isNotBlank()) { "카드 요약은 비어 있을 수 없습니다." }
        // 길이를 맞추는 것은 CardSummary.normalize의 몫이고, 여기서는 그걸 거치지 않은 값이 저장되는
        // 경로가 생기지 않았는지만 확인한다.
        // 길이는 UTF-16 유닛이 아니라 사용자가 보는 글자 수로 센다 — 유닛으로 재면 이모지가 섞인
        // 문장이 normalize를 통과하고도 여기서 거부돼 500이 난다.
        require(CardSummary.graphemeCount(summary) <= CardSummary.MAX_LENGTH) {
            "카드 요약은 ${CardSummary.MAX_LENGTH}자 이하여야 합니다."
        }
        require('\n' !in summary && '\r' !in summary) { "카드 요약은 한 줄이어야 합니다." }
        require(message.isNotBlank()) { "카드 한 줄은 비어 있을 수 없습니다." }
        require('\n' !in message && '\r' !in message) { "카드 한 줄에는 개행이 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /**
     * 삭제 시각. 행을 물리 삭제하지 않고 시각만 남겨 사용자 조회에서만 감춘다 —
     * 백오피스의 생성 이력 통계(오늘 카드 수·감정 분포·일별 추이)는 소급 변동하면 안 되기 때문이다.
     */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    /**
     * 공유 링크(`gamss.kr/c/{토큰}`)가 가리키는 토큰. 공유 버튼을 누른 카드에만 붙으므로 기본은 null 이다
     * — 모든 카드에 미리 발급하면 아직 아무에게도 주지 않은 링크가 쌓이고, 그중 하나만 새어도 열린다.
     */
    @Embedded
    var shareToken: ShareToken? = null
        protected set

    fun isOwnedBy(memberId: Long): Boolean = this.memberId == memberId

    fun isDeleted(): Boolean = deletedAt != null

    /**
     * 삭제 처리. 되돌릴 수 없다 — 대화방당 카드는 하나뿐이고(conversation_id UNIQUE),
     * 카드 생성 상태도 DONE 으로 남아 같은 대화방에 카드를 다시 만들 수 없다.
     */
    fun delete() {
        if (isDeleted()) {
            throw BusinessException(ErrorCode.CARD_ALREADY_DELETED)
        }
        deletedAt = Instant.now()
    }

    /**
     * 공유 토큰을 붙이고 그 토큰을 돌려준다. 이미 붙어 있으면 [token] 을 버리고 원래 것을 그대로 준다
     * — 공유 버튼을 두 번 눌렀다고 링크가 바뀌면 먼저 보낸 링크가 죽는다.
     *
     * 삭제된 카드는 공유할 수 없다. 공유 조회는 삭제된 카드를 감추므로(열어도 404) 토큰만 발급되고
     * 링크는 죽어 있는 상태가 된다.
     */
    fun share(token: ShareToken): ShareToken {
        if (isDeleted()) {
            throw BusinessException(ErrorCode.CARD_ALREADY_DELETED)
        }
        return shareToken ?: token.also { shareToken = it }
    }
}
