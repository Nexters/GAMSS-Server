package com.nexters.gamss.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MySQLContainer

/**
 * 통합/리포지토리 테스트용 MySQL Testcontainer.
 * `@ServiceConnection` 으로 datasource 설정이 자동 주입된다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> = MySQLContainer("mysql:8.0")
}
