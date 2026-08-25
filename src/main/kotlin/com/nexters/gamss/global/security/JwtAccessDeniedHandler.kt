package com.nexters.gamss.global.security

import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.ErrorResponse
import com.nexters.gamss.global.response.httpStatusOf
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 인증은 됐지만 권한이 부족한 요청(예: ROLE_ADMIN 없이 백오피스 API 호출)에 공통 응답 포맷으로 403을 반환한다.
 * (Spring Security 기본 핸들러는 빈 바디를 내려 ApiResponse 포맷과 어긋난다.)
 */
@Component
class JwtAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val body =
            ApiResponse.error(
                ErrorResponse(ErrorCode.ACCESS_DENIED.code, ErrorCode.ACCESS_DENIED.message),
            )
        response.status = httpStatusOf(ErrorCode.ACCESS_DENIED.kind).value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
