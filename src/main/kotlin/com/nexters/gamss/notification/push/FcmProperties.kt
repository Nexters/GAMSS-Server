package com.nexters.gamss.notification.push

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * FCM 발송 설정.
 *
 * [credentialsBase64] 는 Firebase 서비스 계정 JSON 을 base64 로 한 줄로 만든 값이다. 원본 JSON 은
 * 여러 줄이라 `.env` 한 줄에 담기지 않아서(배포가 시크릿을 그렇게 전달한다) 인코딩해서 넘긴다.
 *
 * 비어 있으면 발송하지 않는 구현이 선택된다([PushConfig]) — 로컬·테스트가 자격증명 없이도 뜨게
 * 하려는 것이다. 이 값은 프로젝트 전체를 다룰 수 있는 자격증명이므로 설정 파일에 넣지 않는다.
 *
 * 소셜 로그인 검증 설정([com.nexters.gamss.auth.social.FirebaseProperties])과 접두사를 나눈 것은
 * 같은 Firebase 프로젝트를 쓰더라도 쓰임이 다르기 때문이다 — 그쪽은 공개해도 되는 프로젝트 ID고
 * 이쪽은 비밀이며, 발송을 다른 서비스로 옮기면 이 설정만 사라진다.
 */
@ConfigurationProperties(prefix = "fcm")
data class FcmProperties(
    val credentialsBase64: String?,
)
