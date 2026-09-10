package com.nexters.gamss.llm.provider

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 지금 어느 [GeminiConnection] 로 호출할지 관리한다. 설정은 DB 단일 행이라 백오피스에서 바꾸면
 * 재배포 없이 다음 호출부터 반영된다.
 */
@Service
class GeminiConnectionService(
    connections: List<GeminiConnection>,
    private val repository: LlmProviderSettingRepository,
) {
    private val byProvider = connections.associateBy { it.provider }

    /**
     * 지금 쓰는 연결. 경로와 클라이언트를 따로 물으면 그 사이에 전환이 끼어들어 서로 다른 경로를
     * 볼 수 있으므로, 한 번에 하나로 돌려준다.
     */
    @Transactional(readOnly = true)
    fun active(): GeminiConnection = connection(requireRow().provider)

    /** 백오피스가 고를 수 있는 경로. 구현체가 없는 값은 고를 수 없으므로 등록된 것만 준다. */
    fun availableProviders(): List<LlmProvider> = LlmProvider.entries.filter { it in byProvider }

    /** 대상 경로가 실제로 쓸 수 있을 때만 바꾼다 — 자격증명이 없는 경로로 넘어가 생성이 죽는 것을 막는다. */
    @Transactional
    fun switchTo(provider: LlmProvider) {
        connection(provider).ensureUsable()
        requireRow().update(provider)
    }

    private fun connection(provider: LlmProvider): GeminiConnection =
        checkNotNull(byProvider[provider]) { "$provider 를 처리할 GeminiConnection 구현체가 없습니다." }

    // 행이 늘어난 적이 없어도 정렬은 고정한다 — 읽기와 쓰기가 다른 행을 잡으면 전환이 조용히 무시된다.
    private fun requireRow(): LlmProviderSetting =
        checkNotNull(repository.findAll(Sort.by("id")).firstOrNull()) {
            "llm_provider_setting 행이 없습니다. V37 시딩 마이그레이션이 적용됐는지 확인하세요."
        }
}
