package com.nexters.gamss.llm.preview

import com.nexters.gamss.emotion.domain.EmotionType

/**
 * 답장 미리보기 실행 조건 - 유저가 [character]의 댓글([characterComment])에 [userReply]로 답장했을 때
 * 그 캐릭터의 재응답을 시험한다. 프롬프트 조각이 null이면 저장된 현재값을 쓴다.
 */
data class ReplyPreviewCommand(
    val commonPrompt: String?,
    val replyPrompt: String?,
    val diaryContent: String,
    val character: EmotionType,
    val characterComment: String,
    val userReply: String,
)
