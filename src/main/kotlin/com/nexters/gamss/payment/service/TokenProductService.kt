package com.nexters.gamss.payment.service

import com.nexters.gamss.payment.domain.TokenProduct
import com.nexters.gamss.payment.repository.TokenProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TokenProductService(
    private val repository: TokenProductRepository,
) {
    /** 지금 판매하는 상품. 가격은 각 스토어 SDK 가 현지 통화로 보여 주므로 여기 없다. */
    fun activeProducts(): List<TokenProduct> = repository.findAllByActiveTrueOrderByTokenAmountAsc()
}
