package com.nexters.gamss.game.controller

import com.nexters.gamss.game.domain.GameRecord
import com.nexters.gamss.game.domain.GameType
import com.nexters.gamss.game.repository.GameRecordRepository
import com.nexters.gamss.global.security.JwtIssuer
import com.nexters.gamss.member.domain.Member
import com.nexters.gamss.member.domain.Nickname
import com.nexters.gamss.member.repository.MemberRepository
import com.nexters.gamss.member.service.MemberService
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
class GameRecordControllerIntegrationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var memberService: MemberService

    @Autowired
    private lateinit var gameRecordRepository: GameRecordRepository

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
    fun `점수를 저장한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"score":87}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.score") { value(87) }
            }

        val saved = gameRecordRepository.findAll().single()
        assertEquals(member.id, saved.memberId)
        assertEquals(GameType.CLICKER, saved.gameType)
    }

    @Test
    fun `점수가 없으면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `음수 점수는 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"score":-1}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_GAME_SCORE") }
            }
    }

    @Test
    fun `모르는 게임이면 400을 반환한다`() {
        val member = memberRepository.save(Member("me@a.com"))

        mockMvc
            .post("/api/games/tetris/records") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(member))
                contentType = MediaType.APPLICATION_JSON
                content = """{"score":10}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `탈퇴한 회원은 남은 토큰으로 저장할 수 없다`() {
        val member = memberRepository.save(Member("me@a.com"))
        val bearer = bearerFor(member)
        memberService.withdraw(member.id)

        mockMvc
            .post(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"score":10}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("WITHDRAWN_MEMBER") }
            }

        assertEquals(0, gameRecordRepository.count())
    }

    @Test
    fun `인증 없이 저장하면 401을 반환한다`() {
        mockMvc
            .post(PATH) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"score":10}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `내 기록은 기본 최신순이고 남의 기록은 보이지 않는다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        saveRecords(me, 30, 10, 20)
        saveRecords(other, 99)

        mockMvc
            .get("$PATH/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.totalElements") { value(3) }
                jsonPath("$.data.content[0].score") { value(20) }
                jsonPath("$.data.content[1].score") { value(10) }
                jsonPath("$.data.content[2].score") { value(30) }
            }
    }

    @Test
    fun `내 기록을 점수 높은 순과 낮은 순으로 조회한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        saveRecords(me, 30, 10, 20)

        mockMvc
            .get("$PATH/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                param("sort", "SCORE_DESC")
            }.andExpect {
                jsonPath("$.data.content[0].score") { value(30) }
                jsonPath("$.data.content[1].score") { value(20) }
                jsonPath("$.data.content[2].score") { value(10) }
            }
        mockMvc
            .get("$PATH/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                param("sort", "SCORE_ASC")
            }.andExpect {
                jsonPath("$.data.content[0].score") { value(10) }
                jsonPath("$.data.content[1].score") { value(20) }
                jsonPath("$.data.content[2].score") { value(30) }
            }
    }

    @Test
    fun `점수가 같으면 최신 기록이 먼저 온다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val (first, second) = saveRecords(me, 50, 50)

        mockMvc
            .get("$PATH/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                param("sort", "SCORE_DESC")
            }.andExpect {
                jsonPath("$.data.content[0].id") { value(second.id) }
                jsonPath("$.data.content[1].id") { value(first.id) }
            }
    }

    @Test
    fun `모르는 정렬 값이면 400을 반환한다`() {
        val me = memberRepository.save(Member("me@a.com"))

        mockMvc
            .get("$PATH/me") {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                param("sort", "RANDOM")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
    }

    @Test
    fun `랭킹은 전체 회원 기록을 기본 점수 높은 순으로 닉네임과 함께 보여준다`() {
        val me = memberRepository.save(Member("me@a.com", nickname = Nickname("나야나")))
        val other = memberRepository.save(Member("other@a.com", nickname = Nickname("너야너")))
        saveRecords(me, 10)
        saveRecords(other, 90)

        mockMvc
            .get(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].nickname") { value("너야너") }
                jsonPath("$.data.content[0].score") { value(90) }
                jsonPath("$.data.content[1].nickname") { value("나야나") }
            }
    }

    @Test
    fun `랭킹에는 회원마다 최고 기록 하나만 나온다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        val myBest = saveRecords(me, 10, 80, 30)[1]
        saveRecords(other, 50, 20)

        mockMvc
            .get(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                jsonPath("$.data.totalElements") { value(2) }
                jsonPath("$.data.content.length()") { value(2) }
                jsonPath("$.data.content[0].id") { value(myBest.id) }
                jsonPath("$.data.content[0].score") { value(80) }
                jsonPath("$.data.content[1].score") { value(50) }
            }
    }

    @Test
    fun `같은 최고 점수가 여러 번이면 먼저 달성한 기록이 랭킹에 나온다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val (first, _) = saveRecords(me, 70, 70)

        mockMvc
            .get(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                jsonPath("$.data.totalElements") { value(1) }
                jsonPath("$.data.content[0].id") { value(first.id) }
            }
    }

    @Test
    fun `랭킹을 점수 낮은 순으로 조회한다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val other = memberRepository.save(Member("other@a.com"))
        saveRecords(me, 10, 80)
        saveRecords(other, 50)

        mockMvc
            .get(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
                param("sort", "SCORE_ASC")
            }.andExpect {
                jsonPath("$.data.content[0].score") { value(50) }
                jsonPath("$.data.content[1].score") { value(80) }
            }
    }

    @Test
    fun `탈퇴한 회원의 기록은 랭킹에서 사라진다`() {
        val me = memberRepository.save(Member("me@a.com"))
        val leaver = memberRepository.save(Member("leaver@a.com"))
        saveRecords(me, 10)
        saveRecords(leaver, 90)

        memberService.withdraw(leaver.id)

        mockMvc
            .get(PATH) {
                header(HttpHeaders.AUTHORIZATION, bearerFor(me))
            }.andExpect {
                jsonPath("$.data.totalElements") { value(1) }
                jsonPath("$.data.content[0].score") { value(10) }
            }
    }

    private fun saveRecords(
        member: Member,
        vararg scores: Int,
    ): List<GameRecord> = scores.map { gameRecordRepository.saveAndFlush(GameRecord(member.id, GameType.CLICKER, it)) }

    private fun bearerFor(member: Member): String = "Bearer ${jwtIssuer.issueAccessToken(member.id)}"

    companion object {
        private const val PATH = "/api/games/clicker/records"
    }
}
