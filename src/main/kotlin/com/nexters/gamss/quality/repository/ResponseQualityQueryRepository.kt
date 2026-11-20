package com.nexters.gamss.quality.repository

import com.nexters.gamss.conversation.domain.ResponseKind
import com.nexters.gamss.emotion.domain.EmotionType
import com.nexters.gamss.quality.domain.HumanState
import com.nexters.gamss.quality.domain.LabelingCandidate
import com.nexters.gamss.quality.domain.ResponseFilter
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 캐릭터 응답을 판정 조건으로 거르는 조회. 메시지, 대화방, 회원, 생성 기록, 판정을 한 번에 엮어야 해서 네이티브로 둔다.
 * 응답 종류는 답장 대상이 유저 메시지면 REPLY, 아니면 COMMENT 다.
 */
@Repository
class ResponseQualityQueryRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {
    fun findResponseIds(
        filter: ResponseFilter,
        offset: Long,
        limit: Int,
    ): List<Long> {
        val (where, params) = where(filter)
        val query = entityManager.createNativeQuery("SELECT m.id $FROM $where ORDER BY m.id DESC LIMIT :limit OFFSET :offset")
        params.forEach { (k, v) -> query.setParameter(k, v) }
        query.setParameter("limit", limit)
        query.setParameter("offset", offset)
        return query.resultList.map { (it as Number).toLong() }
    }

    fun countResponses(filter: ResponseFilter): Long {
        val (where, params) = where(filter)
        val query = entityManager.createNativeQuery("SELECT COUNT(*) $FROM $where")
        params.forEach { (k, v) -> query.setParameter(k, v) }
        return (query.singleResult as Number).toLong()
    }

    /** [adminEmail]이 아직 판정하지 않은 응답 후보를 무작위로 뽑는다. */
    fun findLabelingCandidates(
        adminEmail: String,
        since: Instant,
        exclude: Collection<Long>,
        limit: Int,
    ): List<LabelingCandidate> {
        val excludeClause = if (exclude.isEmpty()) "" else "AND m.id NOT IN (:exclude)"
        val query =
            entityManager.createNativeQuery(
                "SELECT m.id, m.emotion_type, $KIND $FROM " +
                    "WHERE m.sender_type = 'CHARACTER' AND m.created_at >= :since $excludeClause " +
                    "AND NOT EXISTS (SELECT 1 FROM response_human_verdict h WHERE h.message_id = m.id AND h.admin_email = :admin) " +
                    "ORDER BY RAND() LIMIT :limit",
            )
        query.setParameter("since", since)
        query.setParameter("admin", adminEmail)
        query.setParameter("limit", limit)
        if (exclude.isNotEmpty()) {
            query.setParameter("exclude", exclude)
        }
        return query.resultList.map { row ->
            val cols = row as Array<*>
            LabelingCandidate((cols[0] as Number).toLong(), EmotionType.valueOf(cols[1] as String), ResponseKind.valueOf(cols[2] as String))
        }
    }

    private fun where(filter: ResponseFilter): Pair<String, Map<String, Any>> {
        val conditions = mutableListOf("m.sender_type = 'CHARACTER'", "m.created_at >= :from", "m.created_at < :to")
        val params = mutableMapOf<String, Any>("from" to filter.from, "to" to filter.to)
        filter.emotionType?.let {
            conditions += "m.emotion_type = :emotion"
            params["emotion"] = it.name
        }
        filter.kind?.let {
            conditions += "$KIND = :kind"
            params["kind"] = it.name
        }
        when (filter.aiVerdict) {
            null -> {
                Unit
            }

            AI_NOT_JUDGED -> {
                conditions += "v.id IS NULL"
            }

            else -> {
                conditions += "v.verdict = :aiVerdict"
                params["aiVerdict"] = filter.aiVerdict
            }
        }
        filter.awkwardType?.let {
            conditions += "FIND_IN_SET(:awkwardType, v.awkward_types) > 0"
            params["awkwardType"] = it.name
        }
        filter.promptVersion?.let {
            conditions += "g.type_prompt_version = :promptVersion"
            params["promptVersion"] = it
        }
        val mine = "EXISTS (SELECT 1 FROM response_human_verdict h WHERE h.message_id = m.id AND h.admin_email = :admin)"
        val any = "EXISTS (SELECT 1 FROM response_human_verdict h WHERE h.message_id = m.id)"
        when (filter.humanState) {
            HumanState.ALL -> Unit
            HumanState.MINE_DONE -> conditions += mine
            HumanState.MINE_PENDING -> conditions += "NOT $mine"
            HumanState.ANY_DONE -> conditions += any
            HumanState.NONE -> conditions += "NOT $any"
        }
        if (filter.humanState == HumanState.MINE_DONE || filter.humanState == HumanState.MINE_PENDING) {
            params["admin"] = filter.adminEmail
        }
        return "WHERE " + conditions.joinToString(" AND ") to params
    }

    companion object {
        const val AI_NOT_JUDGED = ResponseFilter.AI_NOT_JUDGED

        private const val KIND = "(CASE WHEN t.sender_type = 'USER' THEN 'REPLY' ELSE 'COMMENT' END)"

        private const val FROM =
            "FROM messages m " +
                "JOIN conversations c ON c.id = m.conversation_id AND c.status <> 'DELETED' " +
                "JOIN members mb ON mb.id = c.member_id AND mb.status <> 'WITHDRAWN' " +
                "LEFT JOIN messages t ON t.id = m.replies_to_message_id " +
                "LEFT JOIN response_generation g ON g.trigger_message_id = " +
                "(CASE WHEN t.sender_type = 'USER' THEN t.id ELSE m.root_message_id END) " +
                "LEFT JOIN response_ai_verdict v ON v.id = (SELECT MAX(v2.id) FROM response_ai_verdict v2 WHERE v2.message_id = m.id) "
    }
}
