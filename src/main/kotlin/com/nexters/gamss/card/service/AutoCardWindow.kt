package com.nexters.gamss.card.service

import com.nexters.gamss.card.config.CardProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 자동 종료·카드 생성 배치가 **어떤 대화방을 대상으로 보는지**를 정하는 단 하나의 기준.
 *
 * 이 창을 두 곳이 함께 쓴다 — 새벽 5시에 방을 닫는 배치([DailyAutoCardScheduler])와, 그 30분 전에
 * "곧 닫힌다"고 알리는 리마인더다. 둘이 같은 창을 보지 않으면 **닫히지도 않을 방을 두고 마무리하라고
 * 알리거나**, 알림 없이 닫히는 방이 생긴다. 각자 계산하면 언젠가 어긋나므로 한 곳에 둔다.
 *
 * 하한([createdAfter])은 요약 저장이 배포되기 전에 만들어진 방을 걸러낸다. 그 방들은 요약이 없어
 * 카드를 만들 수 없고, 배치가 손대면 종료만 시켜버린다([CardProperties.autoCardStartDate]).
 */
@Component
class AutoCardWindow(
    private val properties: CardProperties,
) {
    /** 대상으로 삼는 가장 이른 생성 시각. */
    fun createdAfter(): Instant = dayStart(properties.autoCardStartDate)

    /**
     * 지금 시점 기준으로 가장 최근에 지난 하루 경계(KST [DAY_BOUNDARY_HOUR]시).
     *
     * 이 배치가 보는 하루는 자정이 아니라 새벽 [DAY_BOUNDARY_HOUR]시에 바뀐다. 자정을 상한으로 쓰면
     * **0시~5시에 만든 방이 어제에 속하는데도 "오늘 것"으로 분류돼** 하루를 더 미종료로 기다린다.
     *
     * 아직 오늘 경계 전이면 어제 경계가 기준이다 — 스케줄이 밀리거나 수동으로 돌려도, 04:30 에
     * 리마인더가 돌아도 "지난 하루까지"라는 의미가 흔들리지 않는다.
     */
    fun createdBefore(now: ZonedDateTime = ZonedDateTime.now(ZONE)): Instant {
        val todayBoundary = dayStart(now.toLocalDate()).atZone(ZONE)
        return if (now < todayBoundary) todayBoundary.minusDays(1).toInstant() else todayBoundary.toInstant()
    }

    /** [date]의 하루가 시작하는 시각(KST [DAY_BOUNDARY_HOUR]시). */
    private fun dayStart(date: LocalDate): Instant = date.atTime(DAY_BOUNDARY_HOUR, 0).atZone(ZONE).toInstant()

    companion object {
        const val ZONE_ID = "Asia/Seoul"

        /**
         * **이 배치가 보는** 하루 경계(KST). 자정이 아니라 새벽 5시로 잡는다 — 새벽까지 이어 쓴
         * 기록은 그 전날에 속한다고 보고, 일일 토큰 리셋(`token_policy.reset_hour`, 시드값 5)과
         * 맞춘 값이다.
         *
         * 서비스 전체의 날짜 경계는 아니다 — 카드 캘린더·날짜별 대화 조회는 여전히 자정을 쓴다.
         * 이 상수를 근거로 다른 곳의 날짜 경계를 옮기면 그쪽 조회가 어긋난다.
         */
        const val DAY_BOUNDARY_HOUR = 5

        private val ZONE: ZoneId = ZoneId.of(ZONE_ID)
    }
}
