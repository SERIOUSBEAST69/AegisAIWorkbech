package com.trustai.service;

import com.trustai.exception.BizException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(45);

    private final Random random = new Random();
    private final Map<String, PhoneCodeRecord> phoneCodes = new ConcurrentHashMap<>();

    public PhoneCodePayload issuePhoneCode(String phone) {
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            throw new BizException(40000, "请输入正确的手机号");
        }

        PhoneCodeRecord existing = phoneCodes.get(phone);
        Instant now = Instant.now();
        if (existing != null && existing.expiresAt().isAfter(now)) {
            long elapsedSeconds = Duration.between(existing.issuedAt(), now).getSeconds();
            if (elapsedSeconds < RESEND_INTERVAL.getSeconds()) {
                throw new BizException(40000, "验证码发送过于频繁，请稍后再试");
            }
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        PhoneCodeRecord record = new PhoneCodeRecord(code, now, now.plus(CODE_TTL));
        phoneCodes.put(phone, record);
        return new PhoneCodePayload(phone, code, record.expiresAt().toEpochMilli(), true);
    }

    public void verifyPhoneCode(String phone, String code) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(code)) {
            throw new BizException(40000, "请输入手机号和验证码");
        }

        PhoneCodeRecord record = phoneCodes.get(phone);
        if (record == null) {
            throw new BizException(40000, "验证码不存在，请重新获取");
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            phoneCodes.remove(phone);
            throw new BizException(40000, "验证码已过期，请重新获取");
        }
        if (!record.code().equals(code)) {
            throw new BizException(40000, "验证码错误");
        }

        phoneCodes.remove(phone);
    }

    public record PhoneCodePayload(String phone, String code, long expiresAt, boolean developmentMode) {}

    private record PhoneCodeRecord(String code, Instant issuedAt, Instant expiresAt) {}
}