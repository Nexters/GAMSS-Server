package com.nexters.gamss.global.config

import org.flywaydb.core.api.exception.FlywayValidateException
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 로컬 전용 Flyway 전략. 브랜치를 옮겨다니다 로컬 DB 이력과 마이그레이션 파일의 체크섬이 어긋나
 * 검증에 실패하면(FlywayValidateException), DB를 clean 후 처음부터 재적용해 자동 복구한다.
 *
 * 로컬 DB는 개발용이라 밀어도 무방하다. dev/prod 프로필에선 이 빈이 로드되지 않으므로(@Profile("local"))
 * 운영 DB가 clean 되는 일은 없다. clean 허용은 application-local.yml의 `spring.flyway.clean-disabled: false`.
 */
@Configuration
@Profile("local")
class LocalFlywayConfig {
    @Bean
    fun cleanMigrateOnValidationError(): FlywayMigrationStrategy =
        FlywayMigrationStrategy { flyway ->
            try {
                flyway.migrate()
            } catch (e: FlywayValidateException) {
                flyway.clean()
                flyway.migrate()
            }
        }
}
