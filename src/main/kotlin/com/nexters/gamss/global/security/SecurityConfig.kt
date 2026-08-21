package com.nexters.gamss.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
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
            .headers { headers ->
                // 보안 응답 헤더는 nginx(deploy/nginx/conf/gamss.conf) 한 곳에서만 관리한다.
                // 앱도 같은 헤더를 내보내면 응답에 두 번 실리고, HSTS 는 RFC 6797 상 브라우저가
                // '첫 번째' 것만 처리해 어느 값이 실제로 적용되는지 예측할 수 없다(실제로 앱의
                // max-age=1년이 nginx의 300을 덮고 있었다). nginx 는 앱이 죽은 502 응답과 정적
                // 백오피스까지 커버하므로 적용 범위도 더 넓다.
                //
                // 캐시 방지 헤더(cache-control·pragma·expires)와 x-xss-protection 은 nginx 가
                // 내보내지 않아 중복이 아니므로 그대로 둔다.
                headers.httpStrictTransportSecurity { it.disable() }
                headers.frameOptions { it.disable() }
                headers.contentTypeOptions { it.disable() }
            }.authorizeHttpRequests {
                it.requestMatchers(*publicPaths()).permitAll()
                // 공유 링크로 열리는 카드 조회. 토큰(추측 불가능한 22자)을 아는 것이 곧 볼 권한이라
                // 인증하지 않는다 — 링크를 받은 사람은 앱도 계정도 없을 수 있다.
                //
                // **메서드를 GET 으로 못 박는다.** 메서드를 지정하지 않으면 이 경로의 모든 메서드가
                // 열려, 나중에 같은 경로에 쓰기 API(신고·반응 등)를 붙이는 순간 인증 없이 뚫린다.
                // 지금은 GET 하나뿐이라 뚫린 곳이 없지만, 붙이는 사람이 여기를 다시 볼 거라고
                // 기대하지 않는 편이 안전하다.
                //
                // 발급(POST /api/cards/{cardId}/share)은 본인 카드만 되어야 하므로 공개하지 않는다.
                it.requestMatchers(HttpMethod.GET, "/api/cards/shared/*").permitAll()
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
                // 로그인·재발급만 공개다. 로그아웃은 인증이 필요하므로 /api/auth/** 로 뭉뚱그리지 않는다 —
                // 앞으로 추가되는 인증 API 도 기본은 '보호'가 되게 한다.
                "/api/auth/login",
                "/api/auth/reissue",
                // 관리자 로그인만 공개. /api/admin/** 의 나머지는 ROLE_ADMIN 을 요구한다.
                "/api/admin/auth/login",
                "/actuator/health",
                // 모니터링 서버(gamss-monitor)가 긁어가는 메트릭. 공개 nginx 는 /actuator 전체를
                // 404 로 막고 사설망 전용 블록(9102)만 이 경로를 프록시하므로, 외부에서는 도달할 수
                // 없다 — 인증을 걸면 스크레이프마다 토큰을 관리해야 해서 네트워크 계층에서 통제한다.
                "/actuator/prometheus",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
            )
    }
}
