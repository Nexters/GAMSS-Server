package com.nexters.gamss.tokenlimit.repository

import com.nexters.gamss.tokenlimit.domain.TokenPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface TokenPolicyRepository : JpaRepository<TokenPolicy, Long>
