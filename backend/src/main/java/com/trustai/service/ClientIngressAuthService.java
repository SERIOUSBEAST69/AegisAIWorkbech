package com.trustai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientIngressAuthService {

    @Value("${security.client-ingress.enforce:false}")
    private boolean enforce;

    @Value("${security.client-ingress.token:}")
    private String ingressToken;

    @Value("${security.client-ingress.default-company-id:1}")
    private long defaultCompanyId;

    public boolean isAuthorized(String providedToken) {
        if (!enforce) {
            return true;
        }
        if (!StringUtils.hasText(ingressToken) || !StringUtils.hasText(providedToken)) {
            return false;
        }
        byte[] expected = ingressToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    public Long getDefaultCompanyId() {
        return defaultCompanyId > 0 ? defaultCompanyId : 1L;
    }
}
