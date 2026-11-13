package com.nexters.gamss.quality.domain

enum class AwkwardType {
    OFF_CONTEXT,
    BROKEN_FLOW,
    REPETITIVE,
    UNNATURAL_TONE,
    PERSONA_BREAK,
    USER_CONFUSED,
    OTHER,
    ;

    companion object {
        fun toColumn(types: Collection<AwkwardType>): String? =
            types
                .distinct()
                .sorted()
                .joinToString(",") { it.name }
                .ifEmpty { null }

        fun fromColumn(raw: String?): Set<AwkwardType> =
            if (raw.isNullOrBlank()) {
                emptySet()
            } else {
                raw.split(",").mapTo(sortedSetOf()) {
                    valueOf(it)
                }
            }
    }
}
