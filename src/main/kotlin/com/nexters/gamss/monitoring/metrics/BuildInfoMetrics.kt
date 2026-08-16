package com.nexters.gamss.monitoring.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 지금 어떤 빌드가 돌고 있는지를 메트릭으로 노출한다.
 *
 * 값 자체는 항상 1이고 정보는 라벨(image_tag)에 있다 — 프로메테우스에서 문자열을 다루는 관례적인
 * 방법이다(값은 시계열의 존재를 표시하고, 의미는 라벨이 담는다).
 *
 * 장애를 볼 때 "지금 이 환경에 무엇이 배포돼 있나"는 대시보드가 답해야 하는 첫 질문 중 하나다.
 * 재기동 시각만으로는 재기동이 배포였는지 크래시였는지, 어떤 커밋으로 바뀌었는지 알 수 없다.
 */
@Component
class BuildInfoMetrics(
    registry: MeterRegistry,
    // 배포 스크립트가 .env 에 넣는 이미지 태그(dev-a1b2c3d 형태). 로컬 실행에는 없으므로 기본값을 둔다.
    @Value("\${gamss.build.image-tag:unknown}") imageTag: String,
) {
    init {
        Gauge
            .builder("gamss.build.info") { 1.0 }
            .description("실행 중인 빌드 정보. 값은 항상 1이고 image_tag 라벨이 실제 정보다")
            .tag("image_tag", imageTag)
            .register(registry)
    }
}
