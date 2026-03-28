package com.trustai.exception;

import com.trustai.utils.R;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public R<?> handleBiz(BizException e) {
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleAccessDenied(AccessDeniedException e) {
        return R.error(40300, "无权限");
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleAuthentication(AuthenticationCredentialsNotFoundException e) {
        return R.error(40100, "未登录或令牌失效");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage() == null ? (error.getField() + " 参数不合法") : error.getDefaultMessage())
            .findFirst()
            .orElse("请求参数不合法");
        return R.error(40000, normalizeValidationMessage(message));
    }

    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage() == null ? (error.getField() + " 参数不合法") : error.getDefaultMessage())
            .findFirst()
            .orElse("请求参数不合法");
        return R.error(40000, normalizeValidationMessage(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
            .map(item -> item.getMessage())
            .collect(Collectors.joining("; "));
        if (message == null || message.isBlank()) {
            message = "请求参数不合法";
        }
        return R.error(40000, normalizeValidationMessage(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return R.error(40000, "请求体格式错误或缺少必要字段");
    }

    private String normalizeValidationMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求参数不合法";
        }
        String lower = message.toLowerCase();
        if (lower.contains("must not be blank") || lower.contains("must not be null")) {
            return "不能为空";
        }
        if (lower.contains("size must be between") || lower.contains("must be less than") || lower.contains("length")) {
            return "长度不能超过限制";
        }
        return message;
    }

    @ExceptionHandler(Exception.class)
    public R<?> handle(Exception e) {
        log.error("Unhandled exception", e);
        return R.error(50000, "系统异常，请稍后重试");
    }
}
