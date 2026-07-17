package com.nexters.gamss.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer 토큰을 파싱해 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 인증하지 않고 통과시켜 EntryPoint가 401을 응답하게 한다.
 *
 * access 토큰만 받는다. refresh 토큰은 재발급(reissue)에만 쓰인다.
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
            runCatching { jwtIssuer.parseAccessToken(token) }
                .onSuccess { memberId ->
                    val authentication =
                        UsernamePasswordAuthenticationToken(AuthPrincipal(memberId), null, emptyList())
                    SecurityContextHolder.getContext().authentication = authentication
                }
        }
        filterChain.doFilter(request, response)
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
    }
}
