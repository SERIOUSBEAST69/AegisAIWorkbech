package com.trustai.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AssetContentExtractor {

    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        ".txt", ".csv", ".json", ".xml", ".yml", ".yaml", ".md", ".log", ".sql", ".tsv"
    );

    public String extractPreview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        try {
            return extractPreview(file.getOriginalFilename(), file.getBytes());
        } catch (IOException ignored) {
            return "";
        }
    }

    public String extractPreview(String location) {
        if (!StringUtils.hasText(location)) {
            return "";
        }
        try {
            Path path = Paths.get(location);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                return "";
            }
            return extractPreview(path.getFileName().toString(), Files.readAllBytes(path));
        } catch (Exception ignored) {
            return "";
        }
    }

    public String extractPreview(String originalFilename, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || !isReadableText(originalFilename, bytes)) {
            return "";
        }

        CharsetDecision decision = detectCharset(bytes);
        String primary = decode(bytes, decision);
        String fallback = decision.charset().equals(GB18030) ? primary : decode(bytes, new CharsetDecision(GB18030, 0));
        String selected = score(fallback) > score(primary) ? fallback : primary;
        return normalize(selected);
    }

    private boolean isReadableText(String originalFilename, byte[] bytes) {
        if (StringUtils.hasText(originalFilename)) {
            String lower = originalFilename.toLowerCase();
            for (String extension : TEXT_EXTENSIONS) {
                if (lower.endsWith(extension)) {
                    return true;
                }
            }
        }

        int printable = 0;
        for (byte value : bytes) {
            int current = value & 0xFF;
            if (current == 0) {
                return false;
            }
            if (current == 9 || current == 10 || current == 13 || (current >= 32 && current < 127) || current >= 160) {
                printable++;
            }
        }
        return printable >= bytes.length * 0.85;
    }

    private CharsetDecision detectCharset(byte[] bytes) {
        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new CharsetDecision(StandardCharsets.UTF_8, 3);
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new CharsetDecision(StandardCharsets.UTF_16LE, 2);
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new CharsetDecision(StandardCharsets.UTF_16BE, 2);
        }
        if (isValidUtf8(bytes)) {
            return new CharsetDecision(StandardCharsets.UTF_8, 0);
        }
        return new CharsetDecision(GB18030, 0);
    }

    private boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private String decode(byte[] bytes, CharsetDecision decision) {
        int offset = Math.min(decision.offset(), bytes.length);
        return new String(bytes, offset, bytes.length - offset, decision.charset());
    }

    private int score(String text) {
        if (!StringUtils.hasText(text)) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\uFFFD') {
                score -= 8;
            } else if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                score += 3;
            } else if (Character.isLetterOrDigit(current)) {
                score += 1;
            } else if (Character.isISOControl(current) && current != '\n' && current != '\r' && current != '\t') {
                score -= 4;
            }
        }
        return score;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String compact = text
            .replace("\uFEFF", "")
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (compact.length() > 260) {
            return compact.substring(0, 260) + "...";
        }
        return compact;
    }

    private boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private record CharsetDecision(Charset charset, int offset) { }
}