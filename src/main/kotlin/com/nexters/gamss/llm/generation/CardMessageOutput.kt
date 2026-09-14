package com.nexters.gamss.llm.generation

/**
 * 카드 한 줄 생성 1회의 결과. [usedTokens]는 호출 자체의 과금 단위다.
 *
 * [summary]는 구조만 검증된 값이다(한 줄·비어 있지 않음). 길이를 맞추는 것은
 * [com.nexters.gamss.card.domain.CardSummary]의 몫이라 여기서는 자르지 않는다.
 *
 * [kind]가 [CardLineKind.NONSENSE]면 [summary]는 null이다. 쓸 한 줄이 없다는 판정인데 빈 문자열로 두면,
 * 부르는 쪽이 판정을 보지 않고 그 값을 저장하는 실수가 컴파일러에 걸리지 않고 통과한다.
 */
data class CardMessageOutput(
    val summary: String?,
    val usedTokens: Int,
    val cachedTokens: Int,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val kind: CardLineKind = CardLineKind.EVENT,
) {
    init {
        require((kind == CardLineKind.EVENT) == (summary != null)) { "카드 한 줄은 EVENT 판정일 때만 있어야 합니다." }
        // KDoc이 약속한 "비어 있지 않음"을 타입이 지킨다. 파서만 믿으면 구현을 갈아끼울 때 빈 한 줄이 성공으로
        // 통과하고, 그 값은 재시도 블록 밖의 Card 생성에서 터져 대화방 상태가 PENDING으로 남는다.
        require(summary == null || summary.isNotBlank()) { "카드 한 줄은 비어 있을 수 없습니다." }
    }
}
