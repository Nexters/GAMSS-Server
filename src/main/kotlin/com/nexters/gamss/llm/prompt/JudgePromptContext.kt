package com.nexters.gamss.llm.prompt

/** 응답 채점 한 번의 입력. 생성 때와 같은 맥락을 주되, 어떤 모델이나 프롬프트 버전으로 만들었는지는 넣지 않는다. */
data class JudgePromptContext(
    val characterGuide: String,
    val prior: List<JudgeLine>,
    val userMessage: JudgeLine,
    val repliedTo: JudgeLine? = null,
    val responses: List<JudgeResponseLine>,
    val nextUserMessage: String?,
) {
    fun toUserContent(): String =
        buildString {
            appendLine("[캐릭터 설정]")
            appendLine(characterGuide.trim())
            appendLine()
            appendLine("[이전 대화]")
            if (prior.isEmpty()) {
                appendLine("(없음)")
            }
            prior.forEach { appendLine("${it.speaker}: ${it.text.normalizeForPrompt()}") }
            appendLine()
            appendLine("[유저 메시지]")
            repliedTo?.let { appendLine("(${it.speaker}의 말에 답함: ${it.text.normalizeForPrompt()})") }
            appendLine("${userMessage.speaker}: ${userMessage.text.normalizeForPrompt()}")
            appendLine()
            appendLine("[채점할 응답]")
            responses.forEach { line ->
                val target = line.replyTo?.let { " (${it}에게 답함)" } ?: ""
                appendLine("${line.label} ${line.speaker}$target: ${line.text.normalizeForPrompt()}")
            }
            appendLine()
            appendLine("[다음 유저 메시지]")
            appendLine(nextUserMessage?.normalizeForPrompt() ?: "(없음)")
            append("위 응답들을 라벨마다 채점해 JSON으로 출력해.")
        }
}

data class JudgeLine(
    val speaker: String,
    val text: String,
)

data class JudgeResponseLine(
    val label: String,
    val speaker: String,
    val replyTo: String?,
    val text: String,
)
