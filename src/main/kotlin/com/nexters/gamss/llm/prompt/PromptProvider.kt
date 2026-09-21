package com.nexters.gamss.llm.prompt

import com.nexters.gamss.emotion.domain.EmotionType
import org.springframework.stereotype.Component

/**
 * LLM에 보낼 **유저 콘텐츠**를 조립한다(오늘 일기·과거 요약·이번 응답 조건 등).
 * 시스템 프롬프트의 단일 원본은 DB(llm_settings)이며 백오피스에서 관리·버전 기록된다 —
 * 코드에는 프롬프트 본문을 두지 않는다(V23 이관). 반면 여기의 조립 구조(섹션 헤더·응답 조건
 * 형식)는 파서·검증기와 계약으로 묶여 있어 코드가 소유한다.
 */
@Component
class PromptProvider {
    fun buildUserContent(context: CommentPromptContext): String {
        val characterIds = context.characters.joinToString(", ") { PromptCharacterId.of(it).promptId }
        // 개행을 공백으로 정규화한다 — 그대로 두면 "[오늘 일기]" 같은 섹션 헤더를 흉내 낸 텍스트가
        // 유저 입력(일기·요약 등)에 섞여 들어올 때 프롬프트 구조 자체가 깨질 수 있다(신뢰 불가한 입력이라
        // diaryContent·userReply·characterComment·카드 summary까지 이 파일의 모든 유저/LLM 유래 텍스트에 적용한다).
        val currentConversationSummaryText =
            context.currentConversationSummary?.normalizeForPrompt()?.ifBlank { null } ?: "기록 없음."
        val pastSummaryLines = context.pastSummaries.lines
        val eongttungLine = context.eongttungTopic?.let { "- eongttung 소재: $it" }.orEmpty()
        return buildString {
            appendLine("[오늘 대화] $currentConversationSummaryText")
            if (pastSummaryLines.isNotEmpty()) {
                appendLine("[과거 대화 요약] (다른 날 다른 채팅방 기록. 오늘과 확실히 관련 있을 때만 참고)")
                pastSummaryLines.forEach { appendLine("- $it") }
            }
            appendLine("[오늘 일기]")
            appendLine(context.diaryContent.normalizeForPrompt())
            appendLine("[이번 응답 조건]")
            appendLine("- 등장 캐릭터(전원 포함, 다른 캐릭터 추가 금지): $characterIds")
            appendLine("- tikitaka 개수: 정확히 ${context.tikitakaCount}개")
            if (eongttungLine.isNotEmpty()) appendLine(eongttungLine)
            append("위 조건대로 코멘트 + 티키타카를 JSON으로 출력해.")
        }
    }

    /** 유저가 [characterId] 캐릭터의 댓글에 단 답글에, 그 캐릭터만 다시 반응하게 하는 유저 콘텐츠. */
    fun buildReplyUserContent(
        diaryContent: String,
        characterId: String,
        characterComment: String,
        userReply: String,
    ): String =
        buildString {
            appendLine("[오늘 일기]")
            appendLine(diaryContent.normalizeForPrompt())
            appendLine("[이번 응답 조건]")
            appendLine("- 응답할 캐릭터: $characterId (반드시 이 캐릭터로만 응답, 다른 캐릭터로 바꾸지 마라)")
            appendLine("[네가 방금 남긴 댓글]")
            appendLine(characterComment.normalizeForPrompt())
            appendLine("[유저의 답글]")
            appendLine(userReply.normalizeForPrompt())
            append("위 유저 답글에 대해 네 캐릭터 말투로 답글을 JSON으로 출력해.")
        }

    /**
     * 유저가 보낸 메시지들에서 대표 감정 하나를 분류하게 하는 유저 콘텐츠. 담을 구간은
     * [CardMessageWindow]가 정한다 — 카드 한 줄 생성과 **같은 구간**을 봐야 하기 때문이다.
     */
    fun buildCardEmotionUserContent(userMessages: List<String>): String {
        val recent = CardMessageWindow.recent(userMessages)
        return buildString {
            appendLine("[유저가 보낸 메시지] (시간순)")
            recent.forEach { appendLine("- $it") }
            append("위 메시지들에서 드러나는 유저의 대표 감정 하나를 JSON으로 출력해.")
        }
    }

    /**
     * 카드에 남길 하루 기록 한 줄을 요청하는 유저 콘텐츠. 유저가 보낸 메시지가 사실의 기준이고,
     * 클라이언트 요약은 있을 때만 참고로 싣는다([com.nexters.gamss.llm.generation.CardMessageGenerator] 참고).
     *
     * 메시지 구간은 [CardMessageWindow]가 정하고, 렌더링(시간순 불릿)도 감정 분류
     * ([buildCardEmotionUserContent])와 같게 둔다. 두 호출이 **같은 구간**을 봐야 카드에 적힌 사건과
     * 그 카드의 감정이 하루의 다른 절반에서 나오지 않는다.
     *
     * 감정을 캐릭터 id(`bunno`)가 아니라 한글 라벨(`분노`)로 넘긴다 — 캐릭터 id는 그 자체로 말투를
     * 연상시켜, 캐릭터 말투를 쓰지 말라는 시스템 프롬프트와 반대로 끌어당긴다. 카드 한 줄에서
     * 감정은 '누가 말하는지'가 아니라 '어떤 사건을 고를지'의 기준이므로 감정 이름이면 충분하다.
     *
     * 마지막 지시문은 사건이 있다고 전제하지 않는다. "사건 하나를 골라"라고 시키면 알아볼 수 있는 내용이
     * 없는 입력에서도 LLM이 사건을 지어낸다. 판정([com.nexters.gamss.llm.generation.CardLineKind])부터 하게 한다.
     *
     * [summary]는 [CardMessageWindow.MAX_CHARS]까지만 담는다. 클라이언트 요약은 요청 검증
     * (`@Size(max = 2000)`)에 이미 걸려 여기 닿지 않지만, 프롬프트에 실리기 직전의 마지막 방어선이라
     * 남겨 둔다.
     */
    fun buildCardUserContent(
        emotion: EmotionType,
        userMessages: List<String>,
        summary: String?,
    ): String {
        val recent = CardMessageWindow.recent(userMessages)
        val summaryText = summary?.normalizeForPrompt()?.ifBlank { null }
        return buildString {
            appendLine("[대표 감정] ${emotion.label}")
            // 메시지 없는 대화방은 만들어질 수 없지만(첫 메시지와 함께 생긴다), 비었는데 헤더만 남기면
            // LLM이 "메시지가 없다"는 사실 자체를 판정 근거로 삼는다.
            if (recent.isNotEmpty()) {
                appendLine("[유저가 보낸 메시지] (시간순)")
                recent.forEach { appendLine("- $it") }
            }
            if (summaryText != null) {
                appendLine("[오늘 대화 요약] (참고용. 메시지에 없는 내용은 사실로 쓰지 마라)")
                appendLine(summaryText.truncateForPrompt(CardMessageWindow.MAX_CHARS))
            }
            append("위 입력으로 kind를 먼저 판정하고, EVENT면 유저 시점의 카드 한 줄을 JSON으로 출력해.")
        }
    }

    companion object {
        /**
         * [limit]자를 넘으면 **최근 쪽** [limit]자만 남긴다. 앞에서부터 남기면
         * [CardMessageWindow]가 최근을 남긴 것과 반대 방향이 되어, 같은 구간을 보게 한 의미가 없어진다.
         *
         * 첫 글자가 서로게이트 쌍의 뒷짝이면 함께 버린다 — 그대로 자르면 짝이 깨진 문자가 프롬프트에
         * 실린다([com.nexters.gamss.card.domain.CardSummary]가 같은 이유로 그래핌 단위로 센다).
         */
        private fun String.truncateForPrompt(limit: Int): String {
            if (length <= limit) {
                return this
            }
            val start = length - limit
            return substring(if (Character.isLowSurrogate(this[start])) start + 1 else start)
        }
    }
}
