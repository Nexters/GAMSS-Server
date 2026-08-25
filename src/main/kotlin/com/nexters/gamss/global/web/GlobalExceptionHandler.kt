package com.nexters.gamss.global.web

import com.nexters.gamss.global.exception.BusinessException
import com.nexters.gamss.global.exception.ErrorCode
import com.nexters.gamss.global.response.ApiResponse
import com.nexters.gamss.global.response.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = e.errorCode
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, e.message ?: errorCode.message)))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        val message =
            e.bindingResult.fieldErrors
                .firstOrNull()
                ?.defaultMessage ?: errorCode.message
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, message)))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        val message = e.constraintViolations.firstOrNull()?.message ?: errorCode.message
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, message)))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, "${e.parameterName}은(는) 필수입니다.")))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, "${e.name}의 형식이 올바르지 않습니다.")))
    }

    // 요청 본문(JSON) 파싱 실패 — 잘못된 enum 값, 깨진 JSON 등. 클라이언트 오류이므로 400으로 응답한다.
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, "요청 본문의 형식이 올바르지 않습니다.")))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("처리되지 않은 예외", e)
        val errorCode = ErrorCode.INTERNAL_ERROR
        return ResponseEntity
            .status(httpStatusOf(errorCode.kind))
            .body(ApiResponse.error(ErrorResponse(errorCode.code, errorCode.message)))
    }
}
