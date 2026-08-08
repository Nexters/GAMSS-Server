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

    /** 대표 감정 캐릭터가 유저를 대신해 남기는 카드 한 줄을 요청하는 유저 콘텐츠. */
    fun buildCardUserContent(
        emotion: EmotionType,
        summary: String,
    ): String {
        val characterId = PromptCharacterId.of(emotion).promptId
        return buildString {
            appendLine("[대표 감정 캐릭터] $characterId")
            appendLine("[오늘 대화 요약]")
            appendLine(summary.normalizeForPrompt())
            append("위 캐릭터가 유저를 대신해 불특정 다수에게 남기는, 이 하루를 대표하는 카드 한 줄을 JSON으로 출력해.")
        }
    }
}
