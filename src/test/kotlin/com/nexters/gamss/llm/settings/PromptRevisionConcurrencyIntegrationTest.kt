package com.nexters.gamss.llm.settings

import com.nexters.gamss.global.retry.ConflictRetry
import com.nexters.gamss.llm.prompt.PromptType
import com.nexters.gamss.support.TestcontainersConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 동시 저장이 설정 행 잠금(채번 직렬화)과 재시도(최초 기록 경합)로 전원 성공하는지 실 스레드로
 * 검증한다. mock 단위 테스트는 max+1 계산만 검증할 뿐 잠금이 실제로 직렬화하는지 보여주지
 * 못한다 — 잠금이 없거나 낡은 행을 잡으면 같은 버전을 채번해 유니크 제약에 걸리고, 재시도가
 * 없으면 그 위반이 사용자에게 500으로 노출된다.
 *
 * 실제 커밋으로 잠금·경합을 재현해야 하므로 @Transactional 롤백을 쓰지 않고 직접 정리한다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
class PromptRevisionConcurrencyIntegrationTest {
    @Autowired
    private lateinit var promptRevisionService: PromptRevisionService

    @Autowired
    private lateinit var conflictRetry: ConflictRetry

    @Autowired
    private lateinit var promptRevisionRepository: PromptRevisionRepository

    @Autowired
    private lateinit var llmSettingsRepository: LlmSettingsRepository

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        promptRevisionRepository.deleteAll()
        llmSettingsRepository.deleteAll()
    }

    @Test
    fun `동시 저장이 전원 성공하고 버전이 중복 없이 이어진다`() {
        val threadCount = 8
        val startLine = CyclicBarrier(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        // 설정 행이 없는 최초 기록부터 시작한다 — 앞선 스레드들은 행 생성 경합(재시도)을,
        // 뒤따르는 스레드들은 행 잠금 직렬화를 각각 통과해야 한다.
        val futures =
            (1..threadCount).map { i ->
                executor.submit {
                    try {
                        startLine.await() // 모든 스레드를 동시에 출발시켜 경합을 유도한다
                        conflictRetry.execute {
                            promptRevisionService.savePrompt(PromptType.COMMENT, "동시 저장 프롬프트 $i", "admin$i@gamss.kr")
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertTrue(errors.isEmpty(), "동시 저장 중 예외 발생: $errors")
        val revisions =
            promptRevisionRepository
                .findAllByPromptTypeOrderByVersionDesc(PromptType.COMMENT, PageRequest.of(0, 50))
                .content
        assertEquals(threadCount, revisions.size, "스레드 수만큼 리비전이 기록돼야 한다")
        assertEquals((threadCount downTo 1).toList(), revisions.map { it.version }, "버전은 중복 없이 이어져야 한다")

        // 현재값은 반드시 기록된 리비전 중 하나여야 한다(마지막 커밋 승자).
        val current = llmSettingsRepository.findByPromptType(PromptType.COMMENT)
        assertNotNull(current)
        assertTrue(revisions.any { it.systemPrompt == current.systemPrompt }, "현재값이 리비전과 어긋난다")
    }
}
