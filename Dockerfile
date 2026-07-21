# syntax=docker/dockerfile:1

# ---- Build stage: Gradle로 실행 가능한 boot jar 빌드 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트 먼저 복사(레이어 캐시 활용)
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 소스 복사 후 빌드(테스트는 CI에서 수행하므로 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage: JRE만 포함한 경량 이미지 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 유저로 실행
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080

# JAVA_OPTS로 런타임 JVM 옵션 주입 가능, exec로 java를 PID 1로 두어 SIGTERM 정상 처리
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
