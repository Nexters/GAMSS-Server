package com.nexters.gamss.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer 토큰을 파싱해 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 인증하지 않고 통과시켜 EntryPoint가 401을 응답하게 한다.
 *
 * access(회원) 토큰과 admin(백오피스) 토큰을 모두 받는다. refresh 토큰은 재발급에만 쓰인다.
 * 각 파싱은 종류가 다르면 거부하므로, refresh 토큰으로는 어느 쪽 인증도 통과하지 못한다.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtIssuer: JwtIssuer,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveToken(request)?.let { token ->
            authenticate(token)?.let { SecurityContextHolder.getContext().authentication = it }
        }
        filterChain.doFilter(request, response)
    }

    // 회원 토큰이면 회원 인증을, 관리자 토큰이면 ROLE_ADMIN 인증을 만든다.
    private fun authenticate(token: String): Authentication? {
        runCatching { jwtIssuer.parseAccessToken(token) }.getOrNull()?.let { memberId ->
            return UsernamePasswordAuthenticationToken(AuthPrincipal(memberId), null, emptyList())
        }
        runCatching { jwtIssuer.parseAdminToken(token) }.getOrNull()?.let { email ->
            return UsernamePasswordAuthenticationToken(
                AdminPrincipal(email),
                null,
                listOf(SimpleGrantedAuthority(ADMIN_AUTHORITY)),
            )
        }
        return null
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) {
            return null
        }
        return header.substring(BEARER_PREFIX.length)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val ADMIN_AUTHORITY = "ROLE_ADMIN"
    }
}
