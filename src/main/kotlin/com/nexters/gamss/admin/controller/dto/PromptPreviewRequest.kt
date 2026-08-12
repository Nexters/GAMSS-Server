package com.nexters.gamss.admin.controller.dto

import com.nexters.gamss.emotion.domain.EmotionType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class PromptPreviewRequest(
    @field:Schema(description = "시험할 공통 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "commonPrompt는 20000자 이하여야 합니다.")
    val commonPrompt: String? = null,
    @field:Schema(description = "시험할 댓글 프롬프트(미저장 원본). null이면 저장된 현재값 사용", nullable = true)
    @field:Size(max = 20_000, message = "commentPrompt는 20000자 이하여야 합니다.")
    val commentPrompt: String? = null,
    @field:Schema(description = "샘플 일기(유저 메시지)", example = "오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다")
    @field:NotBlank(message = "diaryContent는 필수입니다.")
    @field:Size(max = 2000, message = "diaryContent는 2000자 이하여야 합니다.")
    val diaryContent: String,
    @field:Schema(description = "현재 채팅방 임시 요약(선택)", nullable = true)
    @field:Size(max = 2_000, message = "currentConversationSummary는 2000자 이하여야 합니다.")
    val currentConversationSummary: String? = null,
    // 프로덕션은 과거 대화방 요약을 무작위로 2개 뽑아 넣는다(PastSummaryPolicy). 실험실은 직접
    // 지정해 [과거 대화 요약] 섹션이 걸리는 프롬프트 지시까지 시험할 수 있게 한다.
    @field:Schema(description = "과거 대화 요약 목록(선택, 최대 5개). 프로덕션은 무작위 2개를 주입한다", nullable = true)
    @field:Size(max = 5, message = "pastSummaries는 5개 이하여야 합니다.")
    val pastSummaries: List<
        @Size(max = 2_000, message = "과거 요약 한 줄은 2000자 이하여야 합니다.")
        String,
    >? = null,
    @field:Schema(
        description = "등장 캐릭터(1명 이상 필수). 신규 생성에서 빠진 캐릭터(WARM)는 지정 불가",
        example = "[\"JOY\", \"SADNESS\"]",
    )
    @field:NotEmpty(message = "characters는 1개 이상이어야 합니다.")
    val characters: List<EmotionType>,
    // 프로덕션 상한(CharacterSelector.TOTAL_MAX=3)보다 여유를 둔 실험 상한. 오타(예: 1000)가
    // 그대로 프롬프트에 박혀 출력 토큰이 나가는 것을 막는다.
    @field:Schema(description = "티키타카 개수(0~5). 생략 시 0", example = "1", nullable = true)
    @field:Min(0, message = "tikitakaCount는 0 이상이어야 합니다.")
    @field:Max(5, message = "tikitakaCount는 5 이하여야 합니다.")
    val tikitakaCount: Int? = null,
)
