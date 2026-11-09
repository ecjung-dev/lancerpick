package com.example.lancerpick.common.exception;

import com.example.lancerpick.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException exception, HttpServletRequest request) {
        return error(exception.status(), exception.code(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(e.getField(), "INVALID_VALUE", "입력값을 확인해 주세요."))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.", fields, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력 형식과 범위를 확인해 주세요.", List.of(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> notFound(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 대상을 찾을 수 없습니다.", List.of(), request);
    }

    /**
     * 클라이언트가 보낸 요청 형식 문제는 서버 오류(500)가 아니다. ERROR 로그도 남기지 않는다.
     * 406: Accept가 응답 형식과 맞지 않음(예: PDF 주소에 application/json만 요청).
     * 405: 경로가 지원하지 않는 HTTP 메서드. 415: 지원하지 않는 요청 본문 형식(Content-Type).
     * 406에서 클라이언트가 JSON도 받지 않으면 이 본문을 쓸 수 없어, Spring 기본 처리기가 본문 없는 406으로 응답한다.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> notAcceptable(HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "요청한 응답 형식을 제공할 수 없습니다.", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        var response = error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.", List.of(), request);
        var allowed = exception.getSupportedHttpMethods();
        if (allowed == null || allowed.isEmpty()) return response;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(allowed.toArray(HttpMethod[]::new)).body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 본문 형식입니다.", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        // 비밀번호·세션·요청 본문과 예외 메시지는 기록하지 않는다.
        log.error("Unhandled failure traceId={} type={}", TraceIdFilter.traceId(request), exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "처리 중 오류가 발생했습니다.", List.of(), request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
            List<ApiError.FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, message, fields, TraceIdFilter.traceId(request)));
    }
}
