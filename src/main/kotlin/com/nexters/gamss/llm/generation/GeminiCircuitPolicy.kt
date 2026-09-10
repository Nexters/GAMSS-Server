package com.nexters.gamss.llm.generation

import com.nexters.gamss.llm.error.LlmFailureKind
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Gemini **전송 호출 하나**를 감싸는 서킷의 임계치와 집계 대상이다. 업스트림이 죽었을 때 매 호출이
 * 타임아웃을 끝까지 기다리는 것을 막는 것이 목적이다 — Web MVC 라 호출 하나가 톰캣 스레드 하나를
 * 붙잡고, 스레드가 포화되면 댓글만이 아니라 로그인·조회를 포함한 API 전체가 멈춘다.
 *
 * ### 감싸는 범위
 *
 * 전송 호출까지다. 응답 검증까지 감싸면 프롬프트 품질 문제가 서비스 전체를 멈추게 한다. 그래서
 * 검증 실패([LlmFailureKind.VALIDATION])는 애초에 서킷 밖에서 난다.
 *
 * ### 집계 대상
 *
 * 재시도 정책과 같은 분류를 쓴다([LlmFailureRetryPolicy]). 네트워크·5xx·429만 세고 400·401·403은
 * 실패로도 **성공으로도** 세지 않는다 — 설정 오류는 서킷이 열려봐야 고쳐지지 않고, 그렇다고 상대가
 * 멀쩡하다는 증거도 아니다([tellsNothingAboutAvailability]).
 *
 * ### 임계치는 실패 표본 0건 위에서 고른 값이다
 *
 * 이 값들을 정할 때 prod `generation_log` 169건에 실패가 0건이었다. 관측된 장애에 대응한 것이 아니라
 * 실사용자 유입 전에 절벽형 실패를 막을 장치를 미리 깐 것이다. **count-based 윈도우는 시간으로
 * 만료되지 않으므로**, 지금처럼 트래픽이 낮으면 며칠 전 실패 3건이 윈도우에 남아 멀쩡한 날에 서킷을
 * 열 수 있다. 피해는 [WAIT_DURATION_IN_OPEN_STATE]가 제한하지만, 실패 표본이 쌓이면 임계치를 반드시
 * 다시 봐야 한다.
 */
internal object GeminiCircuitPolicy {
    /** 시간 기반으로 두면 최소 호출 수를 못 채워 영영 안 열린다. 지금 트래픽에서는 개수로 세야 한다. */
    const val SLIDING_WINDOW_SIZE = 10

    /** 초기 1~2건 실패로 열리지 않게 하는 하한. */
    const val MINIMUM_NUMBER_OF_CALLS = 5

    /** 최근 5건 중 3건이 실패하면 연다. */
    const val FAILURE_RATE_THRESHOLD = 50.0f

    /** 잘못 열렸을 때의 피해를 제한한다. 이 시간이 지나면 스스로 탐침을 시작한다. */
    val WAIT_DURATION_IN_OPEN_STATE: Duration = Duration.ofSeconds(30)

    const val PERMITTED_CALLS_IN_HALF_OPEN_STATE = 3

    /**
     * 서킷 설정 하나를 만든다.
     *
     * [clock]을 받는 이유는 테스트 때문이다. 라이브러리 기본값은 경과 시간을 `System.nanoTime()`으로
     * 재서 주입한 시계를 무시하므로, `currentTimestampFunction`까지 시계에서 읽도록 함께 바꿔야
     * 가짜 시계가 실제로 먹는다. 그래야 open → half-open 회복을 30초 자지 않고 검증할 수 있다
     * ([LlmRetryExecutor]가 [RetrySleeper]를 받는 것과 같은 이유다).
     *
     * 프로덕션은 기본값(시스템 시계)을 쓴다. 임계치는 테스트와 프로덕션이 같은 것을 쓰게 해서, 테스트가
     * 검증하는 값과 실제로 도는 값이 갈라지지 않게 한다.
     *
     * 대신 프로덕션의 대기 시간도 단조 시계가 아니라 벽시계로 재게 된다. NTP 가 시계를 뒤로 돌리면
     * 그만큼 오픈 상태가 길어지는데, 30초짜리 대기라 실질적인 영향은 없다고 보고 택했다.
     */
    fun config(clock: Clock = Clock.systemUTC()): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(SLIDING_WINDOW_SIZE)
            .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
            .failureRateThreshold(FAILURE_RATE_THRESHOLD)
            .waitDurationInOpenState(WAIT_DURATION_IN_OPEN_STATE)
            .permittedNumberOfCallsInHalfOpenState(PERMITTED_CALLS_IN_HALF_OPEN_STATE)
            // 트래픽이 없어도 스스로 회복을 시도한다. 새벽 배치처럼 다음 호출이 하루 뒤인 경로가 있어서,
            // 다음 호출을 기다려 전이하는 기본 동작이면 서킷이 열린 채로 하루를 넘긴다.
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .clock(clock)
            .currentTimestampFunction({ it.millis() }, TimeUnit.MILLISECONDS)
            .ignoreException(::tellsNothingAboutAvailability)
            .build()

    /**
     * 이 예외가 업스트림 가용성에 대해 **아무 말도 하지 않는지**. 그런 호출은 성공으로도 실패로도
     * 세지 않고 윈도우에서 통째로 뺀다.
     *
     * `ignoreException` 이지 `recordException` 이 아닌 것이 중요하다. 집계 대상에서 빼기만 하면
     * (`recordException` 이 false 를 주면) 라이브러리는 그 호출을 **성공으로 센다**. 400·401·403 은
     * Gemini 가 멀쩡하다는 증거가 아니므로 성공으로 세면 안 된다 - 특히 half-open 탐침 세 번이
     * 설정 오류만 만나면, 회복한 적이 없는데 서킷이 닫혀 죽은 업스트림으로 트래픽이 다시 쏟아진다.
     *
     * 판단은 재시도 쪽과 같은 번역기를 거친다([GeminiFailureKinds]) - 분류가 두 벌이 되면 한쪽만
     * 고치고 지나치게 된다. 여기서 걸러지지 않은 나머지는 전부 실패로 센다(라이브러리 기본값).
     */
    fun tellsNothingAboutAvailability(error: Throwable): Boolean = GeminiFailureKinds.of(error) !in AVAILABILITY_KINDS

    /** 업스트림이 아프다는 신호로 받아들이는 실패. 재시도 정책이 다시 부르기로 한 것들과 같다. */
    private val AVAILABILITY_KINDS = setOf(LlmFailureKind.CALL, LlmFailureKind.RATE_LIMITED)
}
