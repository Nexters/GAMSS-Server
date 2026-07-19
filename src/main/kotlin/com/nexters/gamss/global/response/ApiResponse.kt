package com.nexters.gamss.global.response

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 모든 API의 공통 응답 포맷.
 */
data class ApiResponse<T>(
    @field:Schema(description = "요청 성공 여부")
    val success: Boolean,
    @field:Schema(description = "성공 시 응답 데이터 (실패 시 null)")
    val data: T? = null,
    @field:Schema(description = "실패 시 에러 정보 (성공 시 null). error.code 로 분기한다.")
    val error: ErrorResponse? = null,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun success(): ApiResponse<Unit> = ApiResponse(success = true)

        fun error(error: ErrorResponse): ApiResponse<Nothing> = ApiResponse(success = false, error = error)
    }
}
