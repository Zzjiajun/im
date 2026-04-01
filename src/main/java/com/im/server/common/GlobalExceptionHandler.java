package com.im.server.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        log.debug("BusinessException: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResponse<Void> handleValidationException(Exception exception) {
        String message = "请求参数不合法";
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException
            && methodArgumentNotValidException.getBindingResult().hasFieldErrors()) {
            message = methodArgumentNotValidException.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        if (exception instanceof BindException bindException
            && bindException.getBindingResult().hasFieldErrors()) {
            message = bindException.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMultipart(MultipartException exception) {
        Throwable root = exception.getRootCause() != null ? exception.getRootCause() : exception;
        log.warn("Multipart failed: {} — {}", root.getClass().getSimpleName(), root.getMessage());
        return ApiResponse.fail(
            "上传中断或请求体不完整（常见：客户端 HTTP 超时断开、网络不稳）。大文件请耐心等待勿关页；若仍失败请检查 Nginx/网关是否限制 body 大小与 read_timeout。");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ApiResponse.fail(exception.getMessage());
    }
}
