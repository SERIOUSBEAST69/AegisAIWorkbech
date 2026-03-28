package com.trustai.exception;

import com.trustai.utils.R;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public R<?> handleBiz(BizException e) {
        return R.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public R<?> handleAccessDenied(AccessDeniedException e) {
        return R.error(40300, "无权限");
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public R<?> handleAuthentication(AuthenticationCredentialsNotFoundException e) {
        return R.error(40100, "未登录或令牌失效");
    }

    @ExceptionHandler(Exception.class)
    public R<?> handle(Exception e) {
        return R.error(50000, e.getMessage());
    }
}
