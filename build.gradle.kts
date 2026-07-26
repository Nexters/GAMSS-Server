plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    jacoco
}

group = "com.nexters"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web / JSON
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    // 로컬 실행 시 docker-compose(MySQL) 자동 기동·종료 (배포 산출물에는 미포함)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // DB Migration (Flyway) — spring-boot-flyway 모듈이 Boot 4 자동설정을 제공
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Security · Validation
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JWT — 자체 토큰 발급/파싱(jjwt), 소셜 토큰 서명 검증(nimbus)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.2")

    // API Docs (Swagger)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

    // Actuator (헬스체크)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // LLM — Gemini 공식 SDK (버전은 구현 시 Maven Central에서 최신으로 재확인)
    implementation("com.google.genai:google-genai:1.51.0")

    // google-genai 내부 구현이 Jackson 2(com.fasterxml.jackson.databind)를 쓰고, 그 jar 안에
    // META-INF/services에 Jackson2용 KotlinModule 등록이 딸려 있어서(google-genai 쪽 잔재 추정),
    // 그 클래스가 클래스패스에 없으면 Hibernate의 Jackson JSON 포맷 매퍼 자동 감지(ObjectMapper.findModules())가
    // ServiceConfigurationError로 죽는다. 우리 앱은 Jackson 3(tools.jackson)만 쓰고 이 모듈 자체는
    // 안 쓰지만, 그 ServiceLoader 조회를 만족시키기 위해 runtime에만 추가한다.
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.4")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// 실행 가능한 boot jar만 사용하므로 일반(plain) jar는 생성하지 않는다.
tasks.named("jar") {
    enabled = false
}

ktlint {
    version.set("1.8.0")
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // 부트스트랩 진입점(main)은 단위 테스트 대상이 아니므로 커버리지에서 제외한다.
    classDirectories.setFrom(
        classDirectories.files.map {
            fileTree(it) { exclude("**/GamssApplication*") }
        },
    )
}

// Docker 이미지 빌드의 레이어 캐싱용 태스크. 컴파일·런타임·테스트 classpath 의존성을 미리 모두
// 내려받아, 소스와 분리된 Docker 레이어에 캐시되게 한다(build.gradle 이 그대로면 재다운로드 없음).
// configuration cache 호환: 해석 대상 FileCollection 을 설정 시점에 캡처하고, 실행 시점엔
// project/configurations 를 참조하지 않는다. lenient 로 개별 해석 실패가 빌드를 깨지 않게 한다.
tasks.register("resolveDependencies") {
    val classpaths =
        configurations
            .filter { it.isCanBeResolved }
            .map { it.incoming.artifactView { isLenient = true }.files }
    inputs.files(classpaths)
    doLast {
        classpaths.forEach { it.count() }
    }
}
