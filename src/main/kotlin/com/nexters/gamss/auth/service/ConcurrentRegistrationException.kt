package com.nexters.gamss.auth.service

import com.nexters.gamss.auth.oauth.OAuthProvider

/**
 * 동시 최초 로그인으로 같은 소셜 계정이 중복 가입되려다 유니크 제약에 걸렸음을 나타내는 도메인 예외.
 *
 * 영속성 계층의 예외(DataIntegrityViolationException)를 이 타입으로 번역해, 재시도를 판단하는
 * 상위 계층이 특정 영속성 기술에 의존하지 않게 한다. 재시도하면 앞선 요청이 커밋한 소셜 계정이
 * 조회되어 해소되는, 회복 가능한 경합이다.
 */
class ConcurrentRegistrationException(
    provider: OAuthProvider,
    providerId: String,
) : RuntimeException("동시 가입 경합: provider=$provider, providerId=$providerId")
