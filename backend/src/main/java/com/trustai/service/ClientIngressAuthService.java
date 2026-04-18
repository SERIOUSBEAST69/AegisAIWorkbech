package com.trustai.service;

import com.trustai.config.jwt.JwtUtil;
import com.trustai.exception.BizException;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientIngressAuthService {

    private final JwtUtil jwtUtil;

    @Value("${security.client-ingress.enforce:false}")
    private boolean enforce;

    @Value("${security.client-ingress.token:}")
    private String ingressToken;

    @Value("${security.client-ingress.default-company-id:1}")
    private long defaultCompanyId;

    public ClientIngressAuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public boolean isAuthorized(String providedToken) {
        if (!enforce) {
            return true;
        }
        String token = normalizeToken(providedToken);
        if (!StringUtils.hasText(ingressToken) || !StringUtils.hasText(token)) {
            return false;
        }
        byte[] expected = ingressToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] provided = token.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    public boolean isRegisteredClientToken(String providedToken) {
        String token = normalizeToken(providedToken);
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            return "client_token".equalsIgnoreCase(String.valueOf(claims.get("typ", String.class)));
        } catch (Exception ignore) {
            return false;
        }
    }

    public boolean isAcceptedClientToken(String providedToken) {
        return isRegisteredClientToken(providedToken) || isAuthorized(providedToken);
    }

    public String normalizeToken(String providedToken) {
        String token = String.valueOf(providedToken == null ? "" : providedToken).trim();
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        return token;
    }

    public Long getDefaultCompanyId() {
        if (defaultCompanyId <= 0) {
            throw new BizException(40100, "未配置有效的客户端默认租户");
        }
        return defaultCompanyId;
    }
}
