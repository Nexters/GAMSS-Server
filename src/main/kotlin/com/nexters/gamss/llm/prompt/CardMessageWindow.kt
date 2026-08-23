package com.nexters.gamss.llm.prompt

/**
 * 카드를 만들 때 LLM에 넣을 유저 메시지의 구간. **감정 분류와 카드 한 줄 생성이 같은 구간을 봐야
 * 한다**는 계약을 이 한 곳이 소유한다.
 *
 * 두 호출이 서로 다른 구간을 보면 카드에 적힌 사건과 그 카드의 감정이 하루의 다른 절반에서 나온다 —
 * 아침에 있었던 일이 카드 문구로 뽑혔는데 색과 캐릭터는 저녁 기분으로 붙는 식이다. 상한을 양쪽에
 * 따로 두면 숫자나 방향 한쪽만 고쳐질 때 조용히 그렇게 되므로, 값과 규칙을 함께 묶어 둔다.
 *
 * 최근 것부터 담는 이유는 하루의 감정이 최근 발화에 더 잘 드러나기 때문이다. 상한을 두는 이유는
 * 대화방당 메시지 개수에 제한이 없어서다 — 방마다 LLM을 부르는 새벽 배치
 * ([com.nexters.gamss.card.service.DailyAutoCardScheduler])에서 방 하나가 배치 전체를 늘릴 수 있다.
 */
internal object CardMessageWindow {
    /**
     * 구간의 크기(글자 수). 메시지가 요청 상한(140자)을 꽉 채워도 28개가 들어가고, 실제 길이라면
     * 100개가 넘게 들어간다 — 현실의 대화방은 사실상 전부 통째로 담긴다.
     */
    const val MAX_CHARS = 4000

    /**
     * 카드 한 줄 생성에 넣을 때 메시지를 잇는 구분자. 공백만으로 잇지 않는 것은 경계가 사라지면
     * 서로 다른 두 이야기가 한 문장처럼 읽히기 때문이다.
     */
    const val SEPARATOR = " / "

    /**
     * 메시지 하나가 프롬프트에서 본문 말고 더 쓰는 글자 수. 카드는 [SEPARATOR]로 잇고 감정은
     * `"- "`와 개행으로 감싸는데 마침 둘 다 3자다 — 그래서 예산 하나로 두 렌더링을 다 덮는다.
     *
     * **본문 길이만 재면 안 된다.** 짧은 메시지가 많은 방에서 이 몫이 쌓여 실제 프롬프트가 상한을
     * 넘고, 그러면 [PromptProvider]의 마지막 방어선이 한 번 더 자르면서 카드만 첫 메시지를 조각으로
     * 보게 된다(감정은 그 방어선을 안 타므로 둘이 어긋난다).
     */
    private const val PER_MESSAGE_OVERHEAD = 3

    /**
     * [userMessages]에서 최근 [MAX_CHARS]자만큼을 **시간순으로** 돌려준다. 메시지 중간에서 자르지
     * 않고 통째로 넣거나 뺀다 — 문장 조각이 프롬프트에 실리지 않게 하려는 것이다.
     *
     * 상한을 혼자 넘기는 메시지 하나뿐이어도 빈 목록을 돌려주지 않는다. 그 방도 카드는 받아야 한다.
     */
    fun recent(userMessages: List<String>): List<String> {
        val normalized = userMessages.map { it.normalizeForPrompt() }.filter { it.isNotBlank() }
        val recent = ArrayDeque<String>()
        var totalChars = 0
        for (message in normalized.asReversed()) {
            totalChars += message.length + PER_MESSAGE_OVERHEAD
            if (recent.isNotEmpty() && totalChars > MAX_CHARS) break
            recent.addFirst(message)
        }
        return recent
    }

    /**
     * 카드 한 줄 생성의 입력으로 넣을 한 덩어리. 감정 분류가 보는 [recent]와 **같은 구간**을 그대로
     * 이어 붙인다 — 이어 붙인 길이는 [PER_MESSAGE_OVERHEAD] 덕분에 항상 [MAX_CHARS] 안에 들어온다.
     *
     * 담을 메시지가 하나도 없으면 빈 문자열이다. 부를 쪽에서 그 경우를 정하라는 뜻으로, 여기서
     * 예외를 던지지는 않는다 — 카드 생성 경로는 상태를 되돌린 뒤 실패시킨다
     * ([com.nexters.gamss.card.service.CardService]).
     */
    fun recentAsText(userMessages: List<String>): String = recent(userMessages).joinToString(SEPARATOR)
}
