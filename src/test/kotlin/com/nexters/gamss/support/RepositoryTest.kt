package com.nexters.gamss.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

/**
 * 리포지토리 테스트 베이스. Testcontainers MySQL 을 사용하고, 각 테스트는 트랜잭션 롤백된다.
 */
@SpringBootTest
@Import(TestcontainersConfig::class)
@Transactional
abstract class RepositoryTest
