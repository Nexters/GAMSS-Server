package com.nexters.gamss.architecture

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 계층·모듈 의존 방향을 고정한다. 여기 있는 규칙들은 전부 한 번씩 실제로 깨져 있었고(#191),
 * 고친 뒤 다시 들어오는 것을 막으려고 둔다.
 *
 * 소스의 `import` 줄만 본다. 이 레포는 타입을 전부 import 해서 쓰므로 그것으로 충분하고,
 * 새 의존성(ArchUnit 등) 없이 규칙을 코드로 남길 수 있다. 완전수식 이름(FQN)으로 우회한 참조는
 * 잡지 못한다는 한계는 있다. KDoc 링크는 import 가 아니므로 애초에 대상이 아니다.
 */
class LayerDependencyTest {
    @Test
    fun `도메인은 인프라를 알지 않는다`() {
        assertNoViolation(
            "도메인이 service·repository·controller·config·search 를 import 한다",
            sources().filter { it.layerOf() == "domain" }.flatMap { file ->
                file
                    .gamssImports()
                    .filter { it.layer() in OUTER_LAYERS && !it.isJpaListener() }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `서비스는 컨트롤러를 알지 않는다`() {
        assertNoViolation(
            "서비스가 웹 응답 DTO 를 만든다",
            sources().filter { it.layerOf() == "service" }.flatMap { file ->
                file
                    .gamssImports()
                    .filter { it.layer() == "controller" }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `컨트롤러는 리포지토리를 건너뛰어 잡지 않는다`() {
        assertNoViolation(
            "컨트롤러가 서비스를 건너뛰고 리포지토리를 직접 참조한다",
            sources().filter { it.layerOf() == "controller" }.flatMap { file ->
                file
                    .gamssImports()
                    .filter { it.layer() == "repository" }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `모듈은 다른 모듈의 리포지토리를 직접 잡지 않는다`() {
        assertNoViolation(
            "모듈 경계를 넘어 리포지토리를 직접 참조한다. 주인 모듈의 서비스를 거쳐야 한다",
            sources().flatMap { file ->
                file
                    .gamssImports()
                    .filter { it.layer() == "repository" && it.module() != file.moduleOf() }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `global 은 기능 모듈을 알지 않는다`() {
        assertNoViolation(
            "공용 코드가 특정 기능 모듈에 의존한다",
            sources().filter { it.moduleOf() == "global" }.flatMap { file ->
                file
                    .gamssImports()
                    .filter { it.module() != "global" }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `에러 코드는 HTTP 를 알지 않는다`() {
        assertNoViolation(
            "도메인이 그대로 참조하는 에러 어휘가 웹 프레임워크에 묶인다. HTTP 매핑은 global/web 이 한다",
            sources().filter { it.moduleOf() == "global" && it.layerOf() == "exception" }.flatMap { file ->
                file
                    .imports()
                    .filter { it.startsWith("org.springframework.") }
                    .map { "${file.display()} -> $it" }
            },
        )
    }

    @Test
    fun `서비스를 읽기·쓰기로 가르지 않는다`() {
        assertNoViolation(
            "ReadService·WriteService 라는 이름이 생겼다. 서비스는 '무엇을 위한 것인가'로 가른다",
            sources().flatMap { file ->
                file
                    .readLines()
                    .filter { TECHNICAL_SERVICE_NAME.containsMatchIn(it) }
                    .map { "${file.display()}: ${it.trim()}" }
            },
        )
    }

    @Test
    fun `헥사고날 용어를 쓰지 않는다`() {
        assertNoViolation(
            "Port·Adapter 로 끝나는 이름이 생겼다. 이 레포는 그 어휘를 쓰지 않는다",
            sources().flatMap { file ->
                file
                    .readLines()
                    .filter { HEXAGONAL_NAME.containsMatchIn(it) }
                    .map { "${file.display()}: ${it.trim()}" }
            },
        )
    }

    private fun assertNoViolation(
        rule: String,
        violations: List<String>,
    ) {
        assertTrue(violations.isEmpty(), "$rule\n" + violations.joinToString("\n") { "  - $it" })
    }

    /**
     * 검사 대상 소스. 하나도 못 찾으면 실패시킨다.
     *
     * [MAIN_SOURCE] 가 상대경로라 작업 디렉터리가 프로젝트 루트가 아니면 빈 리스트가 되는데, 그때
     * 규칙들이 전부 "위반 0건"으로 통과해 버린다. 테스트가 깨지는 것보다 아무것도 검사하지 않으면서
     * 초록불인 쪽이 나쁘다.
     */
    private fun sources(): List<Path> =
        MAIN_SOURCE
            .walk()
            .filter { it.extension == "kt" }
            .toList()
            .also { assertTrue(it.isNotEmpty(), "$MAIN_SOURCE 에서 소스를 찾지 못했다. 규칙이 검사되지 않는다") }

    /** `com/nexters/gamss/<module>/<layer>/...` 에서 모듈 이름. */
    private fun Path.moduleOf(): String =
        MAIN_SOURCE
            .resolve(PACKAGE_ROOT)
            .relativize(this)
            .getName(0)
            .toString()

    /** 같은 경로에서 계층 이름. 모듈 바로 아래 파일이면 빈 문자열. */
    private fun Path.layerOf(): String {
        val relative = MAIN_SOURCE.resolve(PACKAGE_ROOT).relativize(this)
        if (relative.nameCount < 3) {
            return ""
        }
        return relative.getName(1).toString()
    }

    private fun Path.display(): String = MAIN_SOURCE.relativize(this).toString()

    private fun Path.imports(): List<String> =
        readLines()
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").trim() }

    private fun Path.gamssImports(): List<String> = imports().filter { it.startsWith("$PACKAGE_PREFIX.") }

    private fun String.module(): String = removePrefix("$PACKAGE_PREFIX.").substringBefore('.')

    private fun String.layer(): String = removePrefix("$PACKAGE_PREFIX.").substringAfter('.').substringBefore('.')

    /**
     * `@EntityListeners` 에 적는 JPA 라이프사이클 리스너인가.
     *
     * 이 참조는 예외로 둔다. `@Convert`·`@Column` 과 같은 JPA **선언**이지 엔티티가 그 리스너를
     * 호출하는 것이 아니기 때문이다(근거는 [com.nexters.gamss.global.crypto.EncryptedStringConverter]
     * 의 KDoc). 걷어내야 하는 것은 행위 의존이고, 그건 인덱스 계산을 리스너로 옮기면서 사라졌다.
     */
    private fun String.isJpaListener(): Boolean = substringAfterLast('.').let { it.endsWith("Listener") || it.endsWith("Converter") }

    private companion object {
        val MAIN_SOURCE: Path = Path("src/main/kotlin")
        const val PACKAGE_PREFIX = "com.nexters.gamss"
        val PACKAGE_ROOT: String = PACKAGE_PREFIX.replace('.', '/')

        /** 도메인 바깥 계층. 도메인이 이 중 하나를 import 하면 방향이 뒤집힌 것이다. */
        val OUTER_LAYERS = setOf("service", "repository", "controller", "config", "search", "crypto")

        /** 선언·주입 필드·타입 참조 어디에서든 잡히도록 이름만 본다. */
        val HEXAGONAL_NAME = Regex("""\b[A-Z]\w*(Port|Adapter)\b""")

        /**
         * 읽기·쓰기라는 **기술 축**으로 서비스를 가른 이름.
         *
         * 이 축을 쓰면 새 기능마다 "이건 읽기인가"를 물어야 하는데, 유스케이스 서비스에도 조회가
         * 있어서 답이 갈린다. 대신 목적으로 가른다(예: ConversationStatsService 는 '얼마나 있는지
         * 센다', ConversationCardGenerationService 는 '카드 생성이 대화방에 요구하는 것').
         */
        val TECHNICAL_SERVICE_NAME = Regex("""\b[A-Z]\w*(Read|Write)Service\b""")
    }
}
