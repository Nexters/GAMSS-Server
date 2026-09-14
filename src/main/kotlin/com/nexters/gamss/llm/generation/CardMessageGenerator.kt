package com.nexters.gamss.llm.generation

import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.llm.settings.LlmSettingsView

/**
 * 유저가 보낸 메시지를 보고, 카드에 남길 하루 기록 한 줄을 생성한다.
 * 구현(Gemini 등)은 교체 가능하다([CommentGenerator]와 같은 패턴).
 *
 * 사실의 기준은 [userMessages]다. 클라이언트가 보내는 [summary]는 온프레미스 모델이 이미 한 번 압축한
 * 값이라, 그 과정에서 없던 내용이 섞여도 서버가 알아챌 방법이 없다. 그래서 요약은 표현을 고르는 참고로만
 * 싣고, 없어도 된다(새벽 배치에는 요약을 만들어 줄 클라이언트가 없다).
 *
 * [emotion]은 문장에 넣을 단어가 아니라 어느 사건을 고를지의 기준이다. 카드 한 줄에 캐릭터 말투는
 * 쓰지 않는다.
 *
 * 알아볼 수 있는 내용이 없는 대화면 한 줄 대신 [CardLineKind.NONSENSE] 판정을 돌려준다. 그날 카드에
 * 무엇을 남길지는 부르는 쪽이 정한다.
 *
 * 시스템 프롬프트는 CARD 타입 원본을 그대로 쓴다(COMMON과 조립하지 않는다). 캐릭터 보이스 카드를
 * 앞에 붙이면 "말투를 지켜라"와 "말투를 쓰지 마라"가 충돌하기 때문이다
 * ([com.nexters.gamss.llm.settings.SystemPromptResolver]가 조립을 막는다).
 */
interface CardMessageGenerator {
    fun generate(
        emotion: EmotionType,
        userMessages: List<String>,
        summary: String?,
    ): CardMessageOutput

    /**
     * 저장된 설정 대신 주어진 모델·시스템 프롬프트로 생성한다 — 플레이그라운드가 미저장 프롬프트를
     * 시험하는 경로([CommentGenerator]와 같은 패턴). 그 외 로직(유저 콘텐츠·파싱)은 [generate]와
     * 같아야 한다.
     */
    fun generate(
        emotion: EmotionType,
        userMessages: List<String>,
        summary: String?,
        settings: LlmSettingsView,
    ): CardMessageOutput
}
