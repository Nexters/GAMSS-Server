package com.nexters.gamss.admin.service

import com.nexters.gamss.admin.config.AdminProperties
import com.nexters.gamss.auth.social.SocialProvider
import com.nexters.gamss.auth.social.SocialTokenVerifier
import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.security.JwtIssuer
import org.springframework.stereotype.Service

/**
 * 백오피스 관리자 로그인. 회원 로그인과 달리 회원 레코드를 만들지 않고,
 * 구글 로그인으로 확인한 이메일이 허용목록에 있을 때만 관리자 토큰을 발급한다.
 */
@Service
class AdminAuthService(
    private val socialTokenVerifier: SocialTokenVerifier,
    private val adminProperties: AdminProperties,
    private val jwtIssuer: JwtIssuer,
) {
    fun login(idToken: String): String {
        val user = socialTokenVerifier.verify(idToken)
        // 이메일만 믿지 않는다. 이메일 소유를 검증하지 않는 제공자로 관리자와 같은 주소를 만들어
        // 권한을 탈취하는 것을 막기 위해, 관리자는 구글 로그인만 허용한다(프론트도 구글만 사용).
        if (user.provider != SocialProvider.GOOGLE) {
            throw BusinessException(ErrorCode.NOT_ADMIN)
        }
        val email = user.email ?: throw BusinessException(ErrorCode.NOT_ADMIN)
        if (!adminProperties.isAllowed(email)) {
            throw BusinessException(ErrorCode.NOT_ADMIN)
        }
        return jwtIssuer.issueAdminToken(email.trim().lowercase())
    }
}
