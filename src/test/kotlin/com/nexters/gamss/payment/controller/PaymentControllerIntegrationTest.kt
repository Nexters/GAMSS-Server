package com.nexters.gamss.payment.controller

import com.jayway.jsonpath.JsonPath
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class PaymentControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()
    }

    @Test
    fun `판매 상품 목록을 지급량 오름차순으로 반환하고 가격은 내려주지 않는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get(PRODUCTS) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.length()") { value(3) }
                jsonPath("$.data[0].productId") { value("token_300000") }
                jsonPath("$.data[0].tokenAmount") { value(300_000) }
                jsonPath("$.data[2].productId") { value("token_1000000") }
                jsonPath("$.data[0].price") { doesNotExist() }
            }
    }

    @Test
    fun `계정 토큰은 UUID 이고 같은 회원이 다시 불러도 같다`() {
        val member = memberRepository.save(Member("me@a.com"))
        identityRepository.upsert(member.id, SUBJECT)

        val first = accountTokenOf(member)

        assertEquals(first, accountTokenOf(member))
        assertEquals(first, UUID.fromString(first).toString())
    }

    @Test
    fun `탈퇴한 회원이 남은 accessToken 으로 부르면 403 과 WITHDRAWN_MEMBER 를 받는다`() {
        val member = memberRepository.save(Member("me@a.com").apply { withdraw() })

        mockMvc
            .get(ACCOUNT_TOKEN) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("WITHDRAWN_MEMBER") }
            }
    }

    @Test
    fun `주체 매핑이 없는 활성 회원은 500 과 INTERNAL_ERROR 를 받는다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get(ACCOUNT_TOKEN) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
            }.andExpect {
                status { isInternalServerError() }
                jsonPath("$.success") { value(false) }
                jsonPath("$.error.code") { value("INTERNAL_ERROR") }
            }
    }

    @Test
    fun `인증 없이 부르면 두 API 모두 401 을 반환한다`() {
        listOf(PRODUCTS, ACCOUNT_TOKEN).forEach { path ->
            mockMvc.get(path).andExpect { status { isUnauthorized() } }
        }
    }

    private fun accountTokenOf(member: Member): String {
        val body =
            mockMvc
                .get(ACCOUNT_TOKEN) {
                    header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.success") { value(true) }
                }.andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.data.accountToken")
    }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    companion object {
        private const val PRODUCTS = "/api/payments/products"
        private const val ACCOUNT_TOKEN = "/api/payments/account-token"
        private val SUBJECT = "c".repeat(64)
    }
}
