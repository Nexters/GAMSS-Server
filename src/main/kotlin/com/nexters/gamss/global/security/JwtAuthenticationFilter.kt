package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
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
 *
 * 인증 실패가 '만료' 때문이면 [EXPIRED_TOKEN_ATTRIBUTE]를 요청에 남긴다. 필터는 DispatcherServlet
 * 앞에서 돌아 GlobalExceptionHandler가 예외를 잡지 못하므로, 실패 사유를 요청에 실어
 * [JwtAuthenticationEntryPoint]가 응답 코드를 고르게 한다.
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
        resolveToken(request)?.let { token -> authenticate(request, token) }
        filterChain.doFilter(request, response)
    }

    /**
     * 회원 토큰이면 회원 인증을, 관리자 토큰이면 ROLE_ADMIN 인증을 만든다.
     * 어느 쪽도 아니면 인증하지 않고, 실패 사유가 만료면 요청에 표시만 남긴다.
     */
    private fun authenticate(
        request: HttpServletRequest,
        token: String,
    ) {
        val accessResult = runCatching { jwtIssuer.parseAccessToken(token) }
        accessResult.getOrNull()?.let { memberId ->
            authenticateAs(UsernamePasswordAuthenticationToken(AuthPrincipal(memberId), null, emptyList()))
            return
        }
        // 만료는 토큰 종류 검사보다 먼저 판정되므로, 만료된 admin 토큰도 여기서 걸러진다.
        if (accessResult.isExpiredToken()) {
            request.setAttribute(EXPIRED_TOKEN_ATTRIBUTE, true)
            return
        }

        val adminResult = runCatching { jwtIssuer.parseAdminToken(token) }
        adminResult.getOrNull()?.let { email ->
            authenticateAs(
                UsernamePasswordAuthenticationToken(
                    AdminPrincipal(email),
                    null,
                    listOf(SimpleGrantedAuthority(ADMIN_AUTHORITY)),
                ),
            )
        }
    }

    private fun authenticateAs(authentication: Authentication) {
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) {
            return null
        }
        return header.substring(BEARER_PREFIX.length)
    }

    /** 파싱 실패 사유가 만료인지. 서명 위조·형식 오류·종류 불일치는 만료가 아니다. */
    private fun Result<*>.isExpiredToken(): Boolean {
        val error = exceptionOrNull()
        return error is BusinessException && error.errorCode == ErrorCode.EXPIRED_TOKEN
    }

    companion object {
        /**
         * 만료된 토큰으로 인증에 실패했음을 [JwtAuthenticationEntryPoint]에 알리는 요청 속성.
         * 값이 true면 401을 [ErrorCode.EXPIRED_TOKEN]으로, 아니면 [ErrorCode.UNAUTHORIZED]로 응답한다.
         */
        const val EXPIRED_TOKEN_ATTRIBUTE = "gamss.expiredToken"

        private const val BEARER_PREFIX = "Bearer "
        private const val ADMIN_AUTHORITY = "ROLE_ADMIN"
    }
}
