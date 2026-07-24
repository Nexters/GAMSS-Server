package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.domain.AdminAccount
import java.time.Instant

/**
 * 관리자 목록 조회용 통합 항목. ENV 부트스트랩과 DB 관리 항목을 한 목록으로 합쳐 보여준다.
 * ENV 항목은 [id]가 없고 삭제할 수 없다([removable]=false). DB 항목도 본인 계정이면 삭제할 수 없다.
 */
data class AdminAccountEntry(
    val id: Long?,
    val email: String,
    val source: AdminAccountSource,
    val removable: Boolean,
    val createdByEmail: String?,
    val createdAt: Instant?,
) {
    companion object {
        fun db(
            account: AdminAccount,
            removable: Boolean,
        ): AdminAccountEntry =
            AdminAccountEntry(account.id, account.email, AdminAccountSource.DB, removable, account.createdByEmail, account.createdAt)

        fun bootstrap(email: String): AdminAccountEntry = AdminAccountEntry(null, email, AdminAccountSource.ENV, false, null, null)
    }
}
