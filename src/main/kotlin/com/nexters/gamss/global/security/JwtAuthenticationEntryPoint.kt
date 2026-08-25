package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.ErrorResponse
import com.nexters.gamss.global.response.httpStatusOf
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 인증되지 않은 요청에 공통 응답 포맷으로 401을 반환한다.
 *
 * 만료된 토큰과 그 외(토큰 없음·서명 위조·형식 오류·종류 불일치)를 다른 에러 코드로 구분한다 —
 * 클라이언트가 '재발급'과 '재로그인'을 첫 응답만으로 분기할 수 있게 하기 위함이다.
 * 만료 여부는 [JwtAuthenticationFilter]가 요청 속성에 남긴다.
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
        val errorCode = errorCodeOf(request)
        val body = ApiResponse.error(ErrorResponse(errorCode.code, errorCode.message))
        response.status = httpStatusOf(errorCode.kind).value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    private fun errorCodeOf(request: HttpServletRequest): ErrorCode {
        if (request.getAttribute(JwtAuthenticationFilter.EXPIRED_TOKEN_ATTRIBUTE) == true) {
            return ErrorCode.EXPIRED_TOKEN
        }
        return ErrorCode.UNAUTHORIZED
    }
}
