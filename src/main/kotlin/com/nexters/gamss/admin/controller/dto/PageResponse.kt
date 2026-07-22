package com.nexters.gamss.admin.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * 백오피스 목록 API 공통 페이지 응답. Spring Data Page 를 프론트가 다루기 쉬운 형태로 옮긴다.
 */
data class PageResponse<T>(
    @field:Schema(description = "현재 페이지 항목")
    val content: List<T>,
    @field:Schema(description = "현재 페이지 번호(0부터)", example = "0")
    val page: Int,
    @field:Schema(description = "페이지 크기", example = "20")
    val size: Int,
    @field:Schema(description = "전체 항목 수", example = "137")
    val totalElements: Long,
    @field:Schema(description = "전체 페이지 수", example = "7")
    val totalPages: Int,
) {
    companion object {
        fun <E : Any, T : Any> from(
            page: Page<E>,
            mapper: (E) -> T,
        ): PageResponse<T> =
            PageResponse(
                content = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
