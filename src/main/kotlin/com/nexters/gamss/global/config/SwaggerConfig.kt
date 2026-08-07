package com.nexters.gamss.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI(Swagger) 문서 설정. JWT Bearer 인증 스키마를 등록해 Swagger UI에서 토큰을 넣을 수 있게 한다.
 *
 * 문서는 앱 클라이언트용(서비스)과 백오피스용 두 그룹으로 나눈다 — 클라이언트 개발자가
 * 어드민 API에 묻히지 않고 필요한 명세만 보게 하기 위함이다. 드롭다운 정렬 기준은 그룹명이
 * 아니라 **표시명(displayName)** 이라, 표시명의 숫자 접두사가 서비스 문서를 먼저(기본 선택으로)
 * 오게 하는 정렬 키다. 그룹명 접두사는 문서 URL(/v3/api-docs/1-service)에만 남는다.
 */
@Configuration
class SwaggerConfig {
    @Bean
    fun serviceApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("1-service")
            .displayName("1. 서비스 API (앱)")
            .pathsToMatch("/api/**")
            .pathsToExclude("/api/admin/**")
            .build()

    @Bean
    fun adminApi(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("2-admin")
            .displayName("2. 백오피스 API")
            .pathsToMatch("/api/admin/**")
            .build()

    @Bean
    fun openAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("GAMSS API")
                    .description("GAMSS 서비스 API 문서")
                    .version("v0.1"),
            ).addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components().addSecuritySchemes(
                    securitySchemeName,
                    SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
    }
}
