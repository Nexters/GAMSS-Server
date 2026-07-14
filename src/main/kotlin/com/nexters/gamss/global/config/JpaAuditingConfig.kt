package com.nexters.gamss.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing 활성화. @CreatedDate·@LastModifiedDate 가 자동으로 채워진다.
 */
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
