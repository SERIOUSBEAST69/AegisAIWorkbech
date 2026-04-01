package com.trustai.config.jwt;

public record JwtPrincipal(Long userId, String username, Long companyId) {
}
