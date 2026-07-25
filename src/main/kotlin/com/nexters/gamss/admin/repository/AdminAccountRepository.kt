package com.nexters.gamss.admin.repository

import com.nexters.gamss.admin.domain.AdminAccount
import org.springframework.data.jpa.repository.JpaRepository

interface AdminAccountRepository : JpaRepository<AdminAccount, Long> {
    fun existsByEmail(email: String): Boolean

    fun findAllByOrderByCreatedAtAsc(): List<AdminAccount>
}
