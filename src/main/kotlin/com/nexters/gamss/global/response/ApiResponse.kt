package com.nexters.gamss.global.response

/**
 * 모든 API의 공통 응답 포맷.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun success(): ApiResponse<Unit> = ApiResponse(success = true)

        fun error(error: ErrorResponse): ApiResponse<Nothing> = ApiResponse(success = false, error = error)
    }
}
