package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 인증되지 않은 요청에 공통 응답 포맷으로 401을 반환한다.
 */
@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val body =
            ApiResponse.error(
                ErrorResponse(ErrorCode.UNAUTHORIZED.code, ErrorCode.UNAUTHORIZED.message),
            )
        response.status = ErrorCode.UNAUTHORIZED.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
