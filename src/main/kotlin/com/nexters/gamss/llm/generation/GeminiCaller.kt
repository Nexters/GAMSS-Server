package com.nexters.gamss.llm.generation

import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.nexters.gamss.llm.error.LlmFailureKind
import com.nexters.gamss.llm.provider.GeminiConnectionService
import org.springframework.stereotype.Component

/**
 * Gemini 로 **전송 호출이 나가는 유일한 자리**다. 댓글·답글·카드 한 줄·카드 감정 분류 네 곳이 같은
 * 모양의 try/catch 를 각자 들고 있던 것을 모았다.
 *
 * 프롬프트 조립과 응답 파싱은 여기 들어오지 않는다. 그것들은 경로마다 다르고, 무엇보다 **검증까지
 * 이 안에 들어오면 안 되기 때문**이다 — 이 자리는 곧 서킷이 감싸는 범위가 되는데, 프롬프트 품질
 * 문제로 서비스 전체가 멈추게 할 수는 없다.
 *
 * 실패를 어떤 예외로 감쌀지는 [wrapFailure] 로 부르는 쪽이 정한다. 댓글 계열과 카드 계열이 서로 다른
 * 예외를 쓰고 메시지도 다르지만, "SDK 예외를 우리 실패 종류로 번역한다"는 부분만은 같아서다.
 */
@Component
class GeminiCaller(
    private val connections: GeminiConnectionService,
) {
    /**
     * [userContent] 를 [model] 에 보내고 응답을 그대로 돌려준다.
     *
     * 호출 연결을 얻는 것([GeminiConnectionService.active])도 같은 try 안에 있다. 인증 설정이 비어
     * 그 단계에서 나는 `BusinessException` 도 [GeminiFailureKinds] 가 번역하므로, 호출자는 실패가
     * 어느 단계에서 났는지 몰라도 된다.
     */
    fun <E : Exception> call(
        model: String,
        userContent: String,
        config: GenerateContentConfig,
        wrapFailure: (cause: Throwable, kind: LlmFailureKind) -> E,
    ): GenerateContentResponse =
        try {
            connections
                .active()
                .client()
                .models
                .generateContent(model, userContent, config)
        } catch (e: Exception) {
            throw wrapFailure(e, GeminiFailureKinds.of(e))
        }
}
