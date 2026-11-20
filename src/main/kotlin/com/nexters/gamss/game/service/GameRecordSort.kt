package com.nexters.gamss.game.service

import org.springframework.data.domain.Sort

/**
 * 기록 목록 정렬 기준. 각 상수가 자기 정렬을 직접 가져 새 기준은 상수 추가만으로 끝난다.
 * 점수가 같으면 최신 기록이 먼저 오도록 id 로 순서를 고정한다(페이지 사이 중복·누락 방지).
 */
enum class GameRecordSort(
    val sort: Sort,
) {
    LATEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    SCORE_DESC(Sort.by(Sort.Order.desc("score"), Sort.Order.desc("id"))),
    SCORE_ASC(Sort.by(Sort.Order.asc("score"), Sort.Order.desc("id"))),
}
