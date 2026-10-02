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
     * 메시지 하나가 프롬프트에서 본문 말고 더 쓰는 글자 수. 감정 분류와 카드 한 줄 모두 메시지를
     * `"- "`와 개행으로 감싸 3자다([PromptProvider]).
     *
     * **본문 길이만 재면 안 된다.** 짧은 메시지가 많은 방에서 이 몫이 쌓여 실제 프롬프트가 상한을
     * 넘는다. 두 호출의 렌더링이 서로 달라지면 이 예산도 함께 다시 봐야 한다.
     */
    private const val PER_MESSAGE_OVERHEAD = 3

    /**
     * 구간에 담길 수 있는 메시지 수의 상한. 개수가 아니라 글자 수로 자르므로 가장 짧은 메시지(1자)로 채웠을 때가
     * 최대다. 입력 개수를 제한하는 쪽(플레이그라운드 요청)은 이 값을 상한으로 써야 한다 — 더 좁히면 실제 생성이
     * 보는 대화를 미리보기로 재현할 수 없다.
     */
    const val MAX_MESSAGES = MAX_CHARS / (1 + PER_MESSAGE_OVERHEAD)

    /**
     * [userMessages]에서 최근 [MAX_CHARS]자만큼을 **시간순으로** 돌려준다. 메시지 중간에서 자르지
     * 않고 통째로 넣거나 뺀다 — 문장 조각이 프롬프트에 실리지 않게 하려는 것이다.
     *
     * 상한을 혼자 넘기는 메시지 하나뿐이어도 빈 목록을 돌려주지 않는다. 그 방도 카드는 받아야 한다.
     *
     * 담을 메시지가 하나도 없으면 빈 목록이다. 그 경우를 어떻게 다룰지는 부르는 쪽이 정한다 — 카드 생성
     * 경로는 상태를 되돌린 뒤 실패시킨다([com.nexters.gamss.card.service.CardService]).
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
     * 구간에서 가장 먼저 보낸 메시지. 알아볼 수 있는 내용이 없는 대화의 카드에는 이 메시지가 그대로 한 줄로
     * 남는다([com.nexters.gamss.card.service.CardService]).
     *
     * 대화 전체가 아니라 구간에서 고르는 이유는 LLM이 판정할 때 본 메시지여야 하기 때문이다. 구간 밖으로 밀린
     * 메시지는 "힘든 마음이 조금이라도 보이면 NONSENSE로 판정하지 않는다"는 규칙을 거치지 않아, 그런 말이 엉뚱
     * 카드에 그대로 찍힐 수 있다. 현실의 대화방은 통째로 구간에 들어가므로 사용자에게는 대화방의 첫 발화다.
     *
     * 담을 메시지가 없으면 null이다. [recent]와 같이 그 경우를 어떻게 다룰지는 부르는 쪽이 정한다.
     */
    fun first(userMessages: List<String>): String? = recent(userMessages).firstOrNull()
}
