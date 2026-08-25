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
 * 스프링 빈으로 등록해 키를 주입받는다. Hibernate가 SpringBeanContainer로 생성하므로 가능하다.
 *
 * **엔티티가 이 클래스를 직접 참조하는 것은 의도한 것이다.** 도메인은 인프라를 몰라야 한다는 규칙이
 * 있지만 `@Convert` 는 `@Entity`·`@Table`·`@Column` 과 같은 JPA **매핑 선언**이고, 이 레포는 엔티티를
 * JPA 매핑 모델로 쓰기로 이미 정해 두었다. `@Column` 은 받아들이면서 `@Convert` 만 위반으로 보는
 * 기준은 성립하지 않는다.
 *
 * 걷어내야 하는 것은 **행위 의존**이다. 엔티티가 암호화·인덱싱을 스스로 호출하면 그 방식이 바뀔 때
 * 엔티티 시그니처가 따라 바뀐다. 검색 인덱스가 그랬고 그쪽은 리스너로 뺐다
 * ([com.nexters.gamss.conversation.search.MessageSearchIndexListener]). 여기는 다르다. 엔티티는 평문
 * String 만 다루고 변환은 DB 경계에서만 일어나므로, 암호화 방식이 바뀌어도 엔티티 코드는 그대로다.
 *
 * 이 참조까지 없애려면 영속성 모델을 도메인 모델에서 분리해야 하는데, 그건 이 규칙 하나를 위해
 * 치르기에 큰 값이다.
 */
@Component
@Converter
class EncryptedStringConverter(
    private val cipher: TextCipher,
) : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let(cipher::encrypt)

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.let(cipher::decrypt)
}
