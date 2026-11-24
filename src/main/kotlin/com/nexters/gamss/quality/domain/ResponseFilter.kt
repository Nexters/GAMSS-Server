package com.nexters.gamss.quality.domain

import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.emotion.domain.EmotionType
import java.time.Instant

enum class HumanState {
    ALL,
    MINE_DONE,
    MINE_PENDING,
    ANY_DONE,
    NONE,
}

data class ResponseFilter(
    val from: Instant,
    val to: Instant,
    val adminEmail: String,
    val emotionType: EmotionType? = null,
    val kind: ResponseKind? = null,
    /** AWKWARD, NATURAL, UNSURE 또는 아직 채점 안 됨([AI_NOT_JUDGED]). */
    val aiVerdict: String? = null,
    val awkwardType: AwkwardType? = null,
    val promptVersion: Int? = null,
    val humanState: HumanState = HumanState.ALL,
) {
    companion object {
        const val AI_NOT_JUDGED = "NONE"
    }
}

data class LabelingCandidate(
    val messageId: Long,
    val emotionType: EmotionType,
    val kind: ResponseKind,
)
