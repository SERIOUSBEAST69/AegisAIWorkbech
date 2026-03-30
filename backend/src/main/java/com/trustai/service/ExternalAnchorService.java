package com.trustai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalAnchorService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${award.anchor.enabled:true}")
    private boolean anchorEnabled;

    @Value("${award.anchor.key-dir:./target/anchor-keys}")
    private String keyDir;

    @Value("${award.anchor.time-source-url:https://worldtimeapi.org/api/timezone/Etc/UTC}")
    private String timeSourceUrl;

    public Map<String, Object> anchorEvidence(Long companyId, String evidenceType, String evidenceRef, String payloadHash) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("anchored", false);
        result.put("provider", "none");
        result.put("payloadHash", payloadHash);

        if (!anchorEnabled || !StringUtils.hasText(payloadHash)) {
            result.put("message", "anchor disabled or payload hash missing");
            return result;
        }

        String provider = "worldtimeapi+rsa-sign";
        String sourceTime = fetchTrustedTime();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = String.join("|",
            String.valueOf(companyId == null ? 0L : companyId),
            safe(evidenceType),
            safe(evidenceRef),
            payloadHash,
            sourceTime,
            nonce
        );

        String signatureBase64;
        String keyFingerprint;
        String verifyStatus;
        String detailJson;
        try {
            KeyPair keyPair = loadOrCreateKeyPair();
            signatureBase64 = sign(canonical, keyPair.getPrivate());
            boolean verified = verify(canonical, signatureBase64, keyPair.getPublic());
            verifyStatus = verified ? "verified" : "failed";
            keyFingerprint = sha256Base64(keyPair.getPublic().getEncoded());
            Map<String, Object> detail = Map.of(
                "canonical", canonical,
                "verified", verified,
                "timeSourceUrl", timeSourceUrl,
                "generatedAt", Instant.now().toString()
            );
            detailJson = objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            signatureBase64 = "";
            verifyStatus = "failed";
            keyFingerprint = "";
            detailJson = "{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
            provider = "local-fallback";
            log.warn("external anchor failed, fallback detail saved: {}", ex.getMessage());
        }

        jdbcTemplate.update(
            """
            INSERT INTO external_anchor_record
            (company_id, evidence_type, evidence_ref, payload_hash, provider, source_time, nonce, signature_base64, key_fingerprint, verify_status, detail_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            companyId == null ? 0L : companyId,
            safe(evidenceType),
            safe(evidenceRef),
            payloadHash,
            provider,
            sourceTime,
            nonce,
            signatureBase64,
            keyFingerprint,
            verifyStatus,
            detailJson
        );

        Long anchorId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM external_anchor_record", Long.class);
        result.put("anchored", "verified".equalsIgnoreCase(verifyStatus));
        result.put("provider", provider);
        result.put("anchorId", anchorId);
        result.put("sourceTime", sourceTime);
        result.put("nonce", nonce);
        result.put("signatureBase64", signatureBase64);
        result.put("keyFingerprint", keyFingerprint);
        result.put("verifyStatus", verifyStatus);
        return result;
    }

    public Map<String, Object> latestAnchors(Long companyId, int limit) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        return Map.of(
            "rows",
            jdbcTemplate.query(
                """
                SELECT id, evidence_type, evidence_ref, payload_hash, provider, source_time, nonce, key_fingerprint, verify_status, create_time
                FROM external_anchor_record
                WHERE company_id = ?
                ORDER BY id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("evidenceType", rs.getString("evidence_type"));
                    row.put("evidenceRef", rs.getString("evidence_ref"));
                    row.put("payloadHash", rs.getString("payload_hash"));
                    row.put("provider", rs.getString("provider"));
                    row.put("sourceTime", rs.getString("source_time"));
                    row.put("nonce", rs.getString("nonce"));
                    row.put("keyFingerprint", rs.getString("key_fingerprint"));
                    row.put("verifyStatus", rs.getString("verify_status"));
                    row.put("createTime", rs.getTimestamp("create_time"));
                    return row;
                },
                companyId == null ? 0L : companyId,
                safeLimit
            )
        );
    }

    public Map<String, Object> verifyByPayloadHash(Long companyId, String payloadHash) {
        Map<String, Object> row = jdbcTemplate.query(
            """
            SELECT id, payload_hash, verify_status, provider, source_time, key_fingerprint, signature_base64, detail_json, create_time
            FROM external_anchor_record
            WHERE company_id = ? AND payload_hash = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getLong("id"));
                map.put("payloadHash", rs.getString("payload_hash"));
                map.put("verifyStatus", rs.getString("verify_status"));
                map.put("provider", rs.getString("provider"));
                map.put("sourceTime", rs.getString("source_time"));
                map.put("keyFingerprint", rs.getString("key_fingerprint"));
                map.put("signatureBase64", rs.getString("signature_base64"));
                map.put("detailJson", rs.getString("detail_json"));
                map.put("createTime", rs.getTimestamp("create_time"));
                return map;
            },
            companyId == null ? 0L : companyId,
            payloadHash
        );

        if (row == null) {
            return Map.of("found", false, "payloadHash", payloadHash);
        }
        row.put("found", true);
        return row;
    }

    private String fetchTrustedTime() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(timeSourceUrl))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300 && StringUtils.hasText(resp.body())) {
                Map<?, ?> map = objectMapper.readValue(resp.body(), Map.class);
                Object utc = map.get("utc_datetime");
                if (utc != null && StringUtils.hasText(String.valueOf(utc))) {
                    return String.valueOf(utc);
                }
                Object datetime = map.get("datetime");
                if (datetime != null && StringUtils.hasText(String.valueOf(datetime))) {
                    return String.valueOf(datetime);
                }
            }
        } catch (Exception ex) {
            log.warn("trusted time source unavailable: {}", ex.getMessage());
        }
        return Instant.now().toString();
    }

    private KeyPair loadOrCreateKeyPair() throws Exception {
        Path dir = Path.of(keyDir).toAbsolutePath().normalize();
        Path privatePem = dir.resolve("anchor-private.pem");
        Path publicPem = dir.resolve("anchor-public.pem");
        if (Files.exists(privatePem) && Files.exists(publicPem)) {
            return new KeyPair(loadPublicKey(publicPem), loadPrivateKey(privatePem));
        }

        Files.createDirectories(dir);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        writePem(privatePem, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(publicPem, "PUBLIC KEY", keyPair.getPublic().getEncoded());
        return keyPair;
    }

    private void writePem(Path path, String type, byte[] content) throws IOException {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(content);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem, StandardCharsets.UTF_8);
    }

    private PrivateKey loadPrivateKey(Path pemPath) throws Exception {
        String pem = Files.readString(pemPath, StandardCharsets.UTF_8)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(Path pemPath) throws Exception {
        String pem = Files.readString(pemPath, StandardCharsets.UTF_8)
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String sign(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private boolean verify(String message, String signatureBase64, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private String sha256Base64(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(content));
        } catch (Exception ex) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
