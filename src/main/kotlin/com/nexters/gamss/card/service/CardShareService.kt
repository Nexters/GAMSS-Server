package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardShareProperties
import com.nexters.gamss.card.domain.Card
import com.nexters.gamss.card.domain.ShareToken
import com.nexters.gamss.card.repository.CardRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카드 공유 링크의 발급과 공개 조회를 담당한다.
 *
 * 카드 생성·조회·삭제([CardService])와 분리해 둔다 — 공유는 **인증 없이 도달하는 유일한 카드 경로**라
 * 가시성·소유권 판단 기준이 다르고, 링크가 한 번 나가면 회수할 수 없어 규칙을 한곳에 모아 두는 편이
 * 안전하다.
 */
@Service
class CardShareService(
    private val cardRepository: CardRepository,
    private val shareTokenGenerator: ShareTokenGenerator,
    private val cardShareProperties: CardShareProperties,
) {
    /**
     * 본인 카드의 공유 링크를 발급한다. **이미 발급됐으면 같은 링크를 그대로 돌려준다**(멱등) —
     * 공유 버튼을 다시 눌렀다고 링크가 바뀌면 앞서 보낸 링크가 죽는다.
     *
     * 행을 잠그고 읽는다([CardRepository.findByIdForUpdate]) — 잠그지 않으면 동시에 두 번 누른
     * 요청이 각자 '아직 미발급' 을 보고 서로 다른 토큰을 쓴 뒤, 늦게 커밋한 쪽이 먼저 나간 링크를
     * 덮어 죽인다. 삭제와 같은 잠금을 쓰므로 공유 도중 삭제가 끼어들 수도 없다.
     */
    @Transactional
    fun issueShareLink(
        memberId: Long,
        cardId: Long,
    ): String {
        val card =
            cardRepository
                .findByIdForUpdate(cardId)
                .orElseThrow { BusinessException(ErrorCode.CARD_NOT_FOUND) }
        if (!card.isOwnedBy(memberId)) {
            throw BusinessException(ErrorCode.CARD_ACCESS_DENIED)
        }
        return shareUrlOf(card.share(shareTokenGenerator.generate()))
    }

    /**
     * 공유 토큰으로 카드를 연다. 인증이 없으므로 **토큰을 아는 것이 곧 볼 권한**이다.
     *
     * 형식이 틀린 토큰은 DB 까지 가지 않고 여기서 404 로 끊는다 — 링크 자리에 아무 문자열이나 넣는
     * 요청이 곧바로 조회 부하가 되지 않게 한다.
     *
     * 없는 토큰과 지워진 카드를 **같은 404 로 답한다**. 구분해서 알려주면 "이 토큰은 존재하긴 한다"는
     * 사실이 새고, 사용자 입장에서도 링크가 열리지 않는다는 결과는 하나다.
     */
    @Transactional(readOnly = true)
    fun getSharedCard(shareToken: String): Card {
        val token =
            ShareToken.parseOrNull(shareToken)
                ?: throw BusinessException(ErrorCode.CARD_NOT_FOUND)
        return cardRepository
            .findVisibleByShareToken(token.value)
            .orElseThrow { BusinessException(ErrorCode.CARD_NOT_FOUND) }
    }

    private fun shareUrlOf(token: ShareToken): String = "${cardShareProperties.baseUrl}/${token.value}"
}
