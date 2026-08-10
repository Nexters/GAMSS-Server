package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.retry.RecoverableConflictException
import com.nexters.gamss.llm.prompt.PromptType

/**
 * 동시 저장·복원이 같은 리비전 버전을 채번하려다 유니크 제약에 걸렸음을 나타내는 도메인 예외.
 * 잠글 설정 행이 아직 없는 최초 기록에서만 열리는 경합이며, 재시도하면 앞선 요청이 커밋한
 * 상태 위에서 다음 버전으로 해소된다.
 */
class PromptRevisionConflictException(
    promptType: PromptType,
) : RecoverableConflictException("프롬프트 리비전 채번 경합: promptType=$promptType")
