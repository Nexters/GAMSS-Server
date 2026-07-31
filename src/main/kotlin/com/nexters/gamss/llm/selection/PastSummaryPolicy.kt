package com.nexters.gamss.llm.selection

/**
 * 댓글 생성 시 과거 대화방 요약을 얼마나 후보로 삼고([POOL_SIZE]), 그중 몇 개를 실제로 프롬프트에
 * 포함시킬지([PICK_COUNT])에 대한 정책. 무작위 선택 자체는
 * [com.nexters.gamss.conversation.repository.ConversationRepository.findRandomPastSummaries]의
 * SQL(`RAND() LIMIT`)이 수행하고, 여기는 그 개수만 정한다.
 */
internal object PastSummaryPolicy {
    const val POOL_SIZE = 5
    const val PICK_COUNT = 2
}
