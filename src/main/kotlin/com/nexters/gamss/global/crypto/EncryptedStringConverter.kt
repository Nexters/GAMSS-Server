package com.nexters.gamss.global.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

/**
 * 암호화 대상 문자열 컬럼에 붙이는 JPA 컨버터. 엔티티는 평문 String을 그대로 다루고 DB 경계에서만
 * 변환되므로, 서비스·도메인 코드는 암호화를 몰라도 된다.
 *
 * **`autoApply`를 쓰지 않고 컬럼마다 `@Convert`로 명시한다.** 자동 적용하면 회원 이메일·닉네임처럼
 * `like` 검색이 걸린 컬럼까지 암호화돼 그 검색이 조용히 죽는다([com.nexters.gamss.member.repository.MemberRepository]).
 * 무엇을 암호화하는지는 엔티티에서 눈으로 확인할 수 있어야 한다.
 *
 * 스프링 빈으로 등록해 키를 주입받는다 — Hibernate가 SpringBeanContainer로 생성하므로 가능하다.
 */
@Component
@Converter
class EncryptedStringConverter(
    private val cipher: TextCipher,
) : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let(cipher::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.let(cipher::decrypt)
}
