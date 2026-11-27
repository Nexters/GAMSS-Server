package com.nexters.gamss.admin.controller

import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.MemberSocialIdentity
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.repository.MemberSocialIdentityRepository
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class AdminPaidTokenIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jwtIssuer: JwtIssuer

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var identityRepository: MemberSocialIdentityRepository

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
    fun `지급하면 잔액이 늘고 원장에 처리자와 사유가 남는다`() {
        val member = mappedMember()

        adjust(member, "grant", 300_000, "결제 후 미지급 보상").andExpect {
            status { isOk() }
            jsonPath("$.data.appliedAmount") { value(300_000) }
            jsonPath("$.data.balance") { value(300_000) }
            jsonPath("$.data.replayed") { value(false) }
        }

        mockMvc
            .get("/api/admin/members/${member.id}/paid-tokens") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.balance") { value(300_000) }
                jsonPath("$.data.ledger.totalElements") { value(1) }
                jsonPath("$.data.ledger.content[0].type") { value("ADMIN_GRANT") }
                jsonPath("$.data.ledger.content[0].amount") { value(300_000) }
                jsonPath("$.data.ledger.content[0].balanceAfter") { value(300_000) }
                jsonPath("$.data.ledger.content[0].adminEmail") { value(ADMIN_EMAIL) }
                jsonPath("$.data.ledger.content[0].reason") { value("결제 후 미지급 보상") }
            }
    }

    @Test
    fun `같은 멱등 키로 두 번 지급해도 한 번만 반영된다`() {
        val member = mappedMember()
        val key = UUID.randomUUID()

        adjust(member, "grant", 1_000, "장애 보상", key).andExpect { status { isOk() } }
        adjust(member, "grant", 1_000, "장애 보상", key).andExpect {
            status { isOk() }
            jsonPath("$.data.balance") { value(1_000) }
            jsonPath("$.data.replayed") { value(true) }
        }

        mockMvc
            .get("/api/admin/members/${member.id}/paid-tokens") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.balance") { value(1_000) }
                jsonPath("$.data.ledger.totalElements") { value(1) }
            }
    }

    @Test
    fun `잔액보다 많이 회수하면 음수가 되고 원장은 최신순이다`() {
        val member = mappedMember()
        adjust(member, "grant", 1_000, "장애 보상").andExpect { status { isOk() } }

        adjust(member, "revoke", 3_000, "어뷰징 회수").andExpect {
            status { isOk() }
            jsonPath("$.data.appliedAmount") { value(-3_000) }
            jsonPath("$.data.balance") { value(-2_000) }
        }

        mockMvc
            .get("/api/admin/members/${member.id}/paid-tokens") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                jsonPath("$.data.balance") { value(-2_000) }
                jsonPath("$.data.ledger.content[0].type") { value("ADMIN_REVOKE") }
                jsonPath("$.data.ledger.content[1].type") { value("ADMIN_GRANT") }
            }
    }

    @Test
    fun `다른 지급에 쓴 멱등 키로 회수하면 409 IDEMPOTENCY_KEY_REUSED`() {
        val member = mappedMember()
        val key = UUID.randomUUID()
        adjust(member, "grant", 1_000, "장애 보상", key).andExpect { status { isOk() } }

        adjust(member, "revoke", 1_000, "어뷰징 회수", key).andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("IDEMPOTENCY_KEY_REUSED") }
        }
    }

    @Test
    fun `금액이 0 이하이거나 사유가 비거나 멱등 키가 없으면 400 INVALID_INPUT`() {
        val member = mappedMember()
        val invalidBodies =
            listOf(
                """{"idempotencyKey":"${UUID.randomUUID()}","amount":0,"reason":"사유"}""",
                """{"idempotencyKey":"${UUID.randomUUID()}","amount":-1,"reason":"사유"}""",
                """{"idempotencyKey":"${UUID.randomUUID()}","amount":1000,"reason":"  "}""",
                """{"idempotencyKey":"${UUID.randomUUID()}","amount":1000}""",
                """{"amount":1000,"reason":"사유"}""",
                """{"idempotencyKey":"not-a-uuid","amount":1000,"reason":"사유"}""",
            )

        invalidBodies.forEach { body ->
            mockMvc
                .post("/api/admin/members/${member.id}/paid-tokens/grant") {
                    header(HttpHeaders.AUTHORIZATION, adminBearer())
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }
    }

    @Test
    fun `주체 매핑이 없는 회원은 잔액 0과 빈 원장이고 지급은 409다`() {
        val member = memberRepository.save(Member("unmapped@a.com"))

        mockMvc
            .get("/api/admin/members/${member.id}/paid-tokens") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.balance") { value(0) }
                jsonPath("$.data.ledger.totalElements") { value(0) }
            }

        adjust(member, "grant", 1_000, "장애 보상").andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("PAID_TOKEN_SUBJECT_NOT_FOUND") }
        }
    }

    @Test
    fun `없는 회원이면 404 MEMBER_NOT_FOUND`() {
        mockMvc
            .get("/api/admin/members/$MISSING_MEMBER_ID/paid-tokens") {
                header(HttpHeaders.AUTHORIZATION, adminBearer())
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("MEMBER_NOT_FOUND") }
            }
    }

    @Test
    fun `회원 토큰으로는 지급할 수 없다`() {
        val member = mappedMember()

        mockMvc
            .post("/api/admin/members/${member.id}/paid-tokens/grant") {
                header(HttpHeaders.AUTHORIZATION, "Bearer ${jwtIssuer.issueAccessToken(member.id)}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"idempotencyKey":"${UUID.randomUUID()}","amount":1000,"reason":"사유"}"""
            }.andExpect {
                status { isForbidden() }
            }
    }

    private fun adjust(
        member: Member,
        action: String,
        amount: Long,
        reason: String,
        key: UUID = UUID.randomUUID(),
    ): ResultActionsDsl =
        mockMvc.post("/api/admin/members/${member.id}/paid-tokens/$action") {
            header(HttpHeaders.AUTHORIZATION, adminBearer())
            contentType = MediaType.APPLICATION_JSON
            content = """{"idempotencyKey":"$key","amount":$amount,"reason":"$reason"}"""
        }

    private fun mappedMember(): Member {
        val member = memberRepository.save(Member("paid@a.com"))
        identityRepository.save(MemberSocialIdentity(member.id, "%064d".format(member.id)))
        return member
    }

    private fun adminBearer(): String = "Bearer ${jwtIssuer.issueAdminToken(ADMIN_EMAIL)}"

    private companion object {
        const val ADMIN_EMAIL = "admin@gamss.kr"
        const val MISSING_MEMBER_ID = 999_999_999L
    }
}
