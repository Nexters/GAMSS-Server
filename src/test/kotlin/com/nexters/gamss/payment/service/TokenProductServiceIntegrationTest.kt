package com.nexters.gamss.payment.service

import com.nexters.gamss.support.RepositoryTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenProductServiceIntegrationTest : RepositoryTest() {
    @Autowired
    private lateinit var productService: TokenProductService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `판매 상품 목록은 활성 상품만 지급량 오름차순으로 돌려준다`() {
        // 상품 행은 마이그레이션으로만 들어가 엔티티로 만들 경로가 없다.
        jdbcTemplate.update(
            "insert into token_product (product_id, token_amount, active, updated_at) values ('token_retired', 100, false, now(6))",
        )

        val products = productService.activeProducts()

        assertEquals(listOf("token_300000", "token_500000", "token_1000000"), products.map { it.productId })
        assertEquals(listOf(300_000L, 500_000L, 1_000_000L), products.map { it.tokenAmount })
    }
}
