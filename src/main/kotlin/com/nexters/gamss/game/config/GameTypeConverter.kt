package com.nexters.gamss.game.config

import com.nexters.gamss.game.domain.GameType
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** URL 경로의 게임 이름(`clicker`)을 [GameType] 으로 바꾼다. 모르는 이름은 400(INVALID_INPUT)이 된다. */
@Component
class GameTypeConverter : Converter<String, GameType> {
    override fun convert(source: String): GameType =
        GameType.fromPathName(source) ?: throw IllegalArgumentException("지원하지 않는 게임입니다: $source")
}
