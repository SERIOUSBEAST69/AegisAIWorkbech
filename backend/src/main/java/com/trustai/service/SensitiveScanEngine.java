package com.trustai.service;

import com.trustai.client.AiInferenceClient;
import com.trustai.dto.ai.AiBatchClassificationRequest;
import com.trustai.dto.ai.AiBatchClassificationResponse;
import com.trustai.dto.ai.AiClassificationResult;
import com.trustai.dto.SensitiveScanReport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveScanEngine {

    private static final Pattern ID_CARD = Pattern.compile("[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern BANK = Pattern.compile("\\d{12,19}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final AiInferenceClient aiInferenceClient;

    public SensitiveScanReport scan(List<String> texts) {
        List<String> payload = (texts == null || texts.isEmpty()) ? List.of("样本文本") : texts;
        try {
            AiBatchClassificationRequest req = new AiBatchClassificationRequest();
            req.setTexts(payload);
            AiBatchClassificationResponse resp = aiInferenceClient.batchPredict(req);
            if (resp != null && resp.getResults() != null && !resp.getResults().isEmpty()) {
                return buildReport(payload, resp.getResults());
            }
        } catch (Exception e) {
            log.warn("AI inference service unavailable, falling back to regex", e);
        }
        // fallback
        return buildReport(payload, regexClassify(payload));
    }

    private SensitiveScanReport buildReport(List<String> texts, List<AiClassificationResult> results) {
        List<SensitiveScanReport.Result> merged = new ArrayList<>();
        Set<String> sensitiveFields = new HashSet<>();
        int sensitiveCount = 0;
        for (int i = 0; i < texts.size(); i++) {
            AiClassificationResult r = i < results.size() ? results.get(i) : null;
            String label = r == null || r.getLabel() == null ? "unknown" : r.getLabel();
            Double score = r == null ? null : r.getScore();
            merged.add(new SensitiveScanReport.Result(texts.get(i), label, score));
            if (!"unknown".equalsIgnoreCase(label)) {
                sensitiveFields.add(label);
                sensitiveCount++;
            }
        }
        double ratio = texts.isEmpty() ? 0.0 : (sensitiveCount * 100.0 / texts.size());
        SensitiveScanReport report = new SensitiveScanReport();
        report.setResults(merged);
        report.setSummary(new SensitiveScanReport.Summary(new ArrayList<>(sensitiveFields), Math.round(ratio * 100.0) / 100.0, texts.size()));
        return report;
    }

    private List<AiClassificationResult> regexClassify(List<String> texts) {
        List<AiClassificationResult> list = new ArrayList<>();
        for (String text : texts) {
            String label = detectLabel(text == null ? "" : text);
            AiClassificationResult result = new AiClassificationResult();
            result.setLabel(label);
            result.setScore("unknown".equals(label) ? 0.0 : 0.65);
            list.add(result);
        }
        return list;
    }

    private String detectLabel(String text) {
        if (text == null) return "unknown";
        if (ID_CARD.matcher(text).find()) return "id_card";
        if (BANK.matcher(text).find()) return "bank_card";
        if (PHONE.matcher(text).find()) return "phone";
        if (EMAIL.matcher(text).find()) return "email";
        return "unknown";
    }
}
