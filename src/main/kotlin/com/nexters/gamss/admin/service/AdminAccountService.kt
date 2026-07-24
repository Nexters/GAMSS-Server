package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.config.AdminProperties
import com.nexters.gamss.admin.domain.AdminAccount
import com.nexters.gamss.admin.repository.AdminAccountRepository
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 백오피스 접근 허용 관리자 관리. 허용 판정은 `ADMIN_EMAILS` 환경 부트스트랩과 DB의 합집합이다.
 * 부트스트랩은 UI로 지울 수 없는 break-glass라 전 관리자가 잠기는(lockout) 상황을 막고, DB만 UI로 관리한다.
 */
@Service
class AdminAccountService(
    private val adminAccountRepository: AdminAccountRepository,
    private val adminProperties: AdminProperties,
) {
    /** 로그인 허용 여부. 부트스트랩(ENV) 또는 DB에 있으면 허용. */
    @Transactional(readOnly = true)
    fun isAllowed(email: String): Boolean {
        if (adminProperties.isAllowed(email)) {
            return true
        }
        return adminAccountRepository.existsByEmail(adminProperties.normalize(email))
    }

    /**
     * ENV 부트스트랩 + DB 관리 항목을 합쳐 보여준다(ENV 먼저, 그다음 DB 추가순).
     * ENV가 우선이라, ENV에도 있는 DB 항목은 목록에서 숨긴다 — 그 항목을 지워도 ENV로 계속 허용되므로
     * "삭제=즉시 차단" 안내가 어긋나기 때문. 본인 계정은 삭제할 수 없어 removable=false로 표시한다.
     */
    @Transactional(readOnly = true)
    fun list(currentEmail: String): List<AdminAccountEntry> {
        val bootstrap = adminProperties.bootstrapEmails()
        val current = adminProperties.normalize(currentEmail)
        val bootstrapEntries = bootstrap.sorted().map { AdminAccountEntry.bootstrap(it) }
        val dbEntries =
            adminAccountRepository
                .findAllByOrderByCreatedAtAsc()
                .filter { it.email !in bootstrap }
                .map { AdminAccountEntry.db(it, removable = it.email != current) }
        return bootstrapEntries + dbEntries
    }

    /** 관리자를 추가한다. 이미 허용된(ENV·DB) 이메일이면 거부한다. */
    @Transactional
    fun add(
        email: String,
        addedByEmail: String,
    ): AdminAccount {
        val normalized = adminProperties.normalize(email)
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "이메일이 비어 있습니다.")
        }
        if (isAllowed(normalized)) {
            throw BusinessException(ErrorCode.ADMIN_ACCOUNT_ALREADY_EXISTS)
        }
        return try {
            adminAccountRepository.saveAndFlush(AdminAccount(normalized, adminProperties.normalize(addedByEmail)))
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ADMIN_ACCOUNT_ALREADY_EXISTS, e.message)
        }
    }

    /** DB 관리자를 삭제한다. 자기 자신은 삭제할 수 없다(자기잠금 방지). ENV 부트스트랩은 DB에 없어 대상이 아니다. */
    @Transactional
    fun remove(
        id: Long,
        currentEmail: String,
    ) {
        val account =
            adminAccountRepository
                .findById(id)
                .orElseThrow { BusinessException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND) }
        if (account.email == adminProperties.normalize(currentEmail)) {
            throw BusinessException(ErrorCode.CANNOT_REMOVE_SELF)
        }
        adminAccountRepository.delete(account)
    }
}
