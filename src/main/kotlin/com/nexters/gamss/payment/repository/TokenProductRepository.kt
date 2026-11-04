package com.nexters.gamss.payment.repository

import com.nexters.gamss.payment.domain.TokenProduct
import org.springframework.data.jpa.repository.JpaRepository

interface TokenProductRepository : JpaRepository<TokenProduct, String> {
    fun findAllByActiveTrueOrderByTokenAmountAsc(): List<TokenProduct>
}
