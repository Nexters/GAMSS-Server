package com.nexters.gamss.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * 무상태(JWT) 보안 설정. 인증 없이 접근 가능한 경로 외에는 모두 인증을 요구한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAccessDeniedHandler: JwtAccessDeniedHandler,
    private val environment: Environment,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*publicPaths()).permitAll()
                // 백오피스 API는 관리자 토큰(ROLE_ADMIN)만 접근할 수 있다. 로그인만 공개다.
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }.exceptionHandling {
                it.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                it.accessDeniedHandler(jwtAccessDeniedHandler)
            }.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    // 로컬 개발용 dev-login 은 local 프로필에서만 공개한다(컨트롤러도 @Profile("local") 이라 다른 환경엔 없음).
    private fun publicPaths(): Array<String> {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return PUBLIC_PATHS + "/api/admin/auth/dev-login"
        }
        return PUBLIC_PATHS
    }

    companion object {
        private val PUBLIC_PATHS =
            arrayOf(
                "/api/auth/**",
                // 관리자 로그인만 공개. /api/admin/** 의 나머지는 ROLE_ADMIN 을 요구한다.
                "/api/admin/auth/login",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
            )
    }
}
