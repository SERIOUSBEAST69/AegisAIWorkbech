package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.CompliancePolicy;
import com.trustai.entity.AiModel;
import com.trustai.entity.DataAsset;
import com.trustai.entity.RiskEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 模型访问守卫 + 隐私盾
 *
 * <p>自问自答 – Q1 & Q4（数据隐私）：
 * <ul>
 *   <li>如果用户在工作台打开了豆包、OpenClaw 等 AI，并输入了个人隐私信息，工作台能第一时间拦截吗？
 *       → 是的。本服务在请求发送到外部 AI 之前检测输入文本中的敏感信息（身份证、手机、邮箱、银行卡、姓名等），
 *         一旦发现即抛出异常阻断请求，同时写入风险事件日志。</li>
 *   <li>如果 AI 自行窃取输入的信息并非法外传，工作台能第一时间发现并阻拦吗？
 *       → 是的。响应扫描（{@link #scanResponseForExfiltration}）对 AI 返回内容进行逐字段检测，
 *         若响应中意外出现了未脱敏的个人数据（即 AI 将用户输入的隐私原样回传），
 *         立即记录 RESPONSE_EXFILTRATION 级别的高风险事件，并在调用方看到的响应中注入警告。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AiModelAccessGuardService {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ── 隐私输入检测模式（扩展版） ─────────────────────────────────────────────
    /** 中国大陆 18 位身份证号 */
    private static final Pattern ID_CARD = Pattern.compile(
            "[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]"
    );
    /** 手机号（宽松：1[3-9]xxxxxxxx） */
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])1[3-9]\\d{9}(?![\\d])");
    /** 电子邮箱 */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
    );
    /** 银行卡号（12–19 位纯数字） */
    private static final Pattern BANK_CARD = Pattern.compile("(?<![\\d])\\d{12,19}(?![\\d])");
    /** 关键词触发（中文隐私关键字） */
    private static final Pattern SENSITIVE_KEYWORDS = Pattern.compile(
            "(身份证|银行卡|信用卡|手机号|住址|家庭住址|居住地址|真实姓名|" +
            "密码|口令|验证码|工资|薪资|病历|健康记录|基因|宗教信仰|" +
            "phone|email|bank.?card|id.?card|password|private.?key)",
            Pattern.CASE_INSENSITIVE
    );

    private final DataAssetService dataAssetService;
    private final RiskEventService riskEventService;
    private final CompliancePolicyService compliancePolicyService;
    private final CompanyScopeService companyScopeService;

    // ── 公共校验入口 ───────────────────────────────────────────────────────────

    public void validate(AiModel model, Long assetId, String accessReason, String inputText) {
        if (model == null) {
            block("UNREGISTERED_MODEL", "HIGH", null, assetId, "模型未注册，禁止绕过治理目录直接调用");
            throw new IllegalArgumentException("模型未注册，禁止绕过治理目录直接调用");
        }

        if (!isEnabled(model.getStatus())) {
            block("DISABLED_MODEL_CALL", "HIGH", model.getModelCode(), assetId, "模型已停用，不允许调用");
            throw new IllegalStateException("模型已停用，不允许调用");
        }

        DataAsset asset = null;
        if (assetId != null) {
            asset = dataAssetService.getById(assetId);
            if (asset == null) {
                block("INVALID_ASSET_BINDING", "MEDIUM", model.getModelCode(), assetId, "绑定的数据资产不存在");
                throw new IllegalArgumentException("绑定的数据资产不存在: " + assetId);
            }
        }

        boolean highRiskModel = isHighRisk(model.getRiskLevel());
        boolean mediumOrHighRisk = highRiskModel || isMediumRisk(model.getRiskLevel());

        // ── 隐私盾：扫描用户输入文本，阻断含个人隐私的请求 ──────────────────
        List<String> inputDetected = detectPrivacyFields(inputText);
        if (!inputDetected.isEmpty()) {
            String detail = "检测到用户输入含个人隐私字段：" + String.join("、", inputDetected);
            block("PRIVACY_INPUT_BLOCKED", "HIGH", model.getModelCode(), assetId, detail);
            throw new IllegalStateException(
                    "【隐私盾拦截】" + detail + "。请在输入信息脱敏后再发送给 AI 模型。"
            );
        }

        if (highRiskModel && assetId == null) {
            block("HIGH_RISK_MODEL_WITHOUT_ASSET", "HIGH", model.getModelCode(), null, "高风险模型调用必须绑定数据资产");
            throw new IllegalStateException("高风险模型调用必须绑定数据资产");
        }

        if (highRiskModel && (accessReason == null || accessReason.trim().length() < 6)) {
            block("HIGH_RISK_MODEL_WITHOUT_REASON", "HIGH", model.getModelCode(), assetId, "高风险模型调用必须填写不少于 6 个字的访问目的");
            throw new IllegalStateException("高风险模型调用必须填写不少于 6 个字的访问目的");
        }

        if (mediumOrHighRisk && !inputDetected.isEmpty() && assetId == null) {
            block("SENSITIVE_PROMPT_WITHOUT_ASSET", "HIGH", model.getModelCode(), null, "检测到疑似敏感内容，调用中高风险模型时必须绑定数据资产");
            throw new IllegalStateException("检测到疑似敏感内容，调用中高风险模型时必须绑定数据资产");
        }

        if (highRiskModel && asset != null && !isHighSensitivity(asset.getSensitivityLevel()) && accessReason != null && accessReason.length() < 10) {
            block("HIGH_RISK_MODEL_WEAK_REASON", "MEDIUM", model.getModelCode(), assetId, "高风险模型在非高敏资产场景下仍需更明确的调用理由");
            throw new IllegalStateException("高风险模型在当前场景下需要更明确的调用理由");
        }
    }

    /**
     * 响应泄露扫描（Response Exfiltration Detection）
     *
     * <p>对 AI 返回的内容进行隐私扫描。若响应中出现了明文隐私数据（AI 原样回传了用户输入的个人信息），
     * 则记录高风险事件 RESPONSE_EXFILTRATION，并在返回值中注入安全警告。
     *
     * @param responseText AI 返回的原始文本
     * @param modelCode    模型代码（用于日志）
     * @return 若检测到泄露，返回注入警告的文本；否则原样返回
     */
    public String scanResponseForExfiltration(String responseText, String modelCode) {
        if (responseText == null || responseText.isEmpty()) return responseText;
        List<String> detected = detectPrivacyFields(responseText);
        if (!detected.isEmpty()) {
            String detail = "AI 响应中发现疑似个人隐私数据：" + String.join("、", detected);
            block("RESPONSE_EXFILTRATION", "HIGH", modelCode, null, detail);
            return "【⚠️ AegisAI 安全告警】" + detail +
                   "。原始响应已被工作台安全网关标记，请立即联系安全管理员核查数据流向。\n\n" +
                   "[REDACTED BY AEGISAI PRIVACY SHIELD]";
        }
        return responseText;
    }

    // ── 隐私字段检测 ──────────────────────────────────────────────────────────

    /**
     * 检测文本中的隐私字段类型，返回发现的字段类型列表（去重）。
     */
    public List<String> detectPrivacyFields(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> found = new ArrayList<>();
        if (ID_CARD.matcher(text).find())         found.add("身份证号");
        if (PHONE.matcher(text).find())            found.add("手机号");
        if (EMAIL.matcher(text).find())            found.add("电子邮箱");
        if (BANK_CARD.matcher(text).find())        found.add("银行卡号");
        if (SENSITIVE_KEYWORDS.matcher(text).find()) found.add("隐私关键词");
        List<String> customKeywords = loadTenantSensitiveKeywords();
        if (!customKeywords.isEmpty() && containsAnyKeyword(text, customKeywords)) {
            found.add("策略敏感词");
        }
        return new ArrayList<>(new LinkedHashSet<>(found));
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String item : keywords) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (lowered.contains(item.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> loadTenantSensitiveKeywords() {
        try {
            Long companyId = companyScopeService.requireCompanyId();
            List<CompliancePolicy> policies = compliancePolicyService.list(new QueryWrapper<CompliancePolicy>()
                .eq("company_id", companyId)
                .eq("status", 1)
                .and(wrapper -> wrapper.eq("scope", "ai_prompt").or().like("name", "敏感词"))
                .orderByDesc("update_time"));
            if (policies.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (CompliancePolicy policy : policies) {
                merged.addAll(extractKeywords(policy.getRuleContent()));
            }
            return merged.stream().filter(item -> item != null && !item.isBlank()).collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> extractKeywords(String ruleContent) {
        if (ruleContent == null || ruleContent.isBlank()) {
            return List.of();
        }
        String content = ruleContent.trim();
        try {
            if (content.startsWith("{")) {
                Map<String, Object> parsed = JSON.readValue(content, new TypeReference<Map<String, Object>>() { });
                Object keywords = parsed.get("keywords");
                if (keywords instanceof List<?> list) {
                    return list.stream().map(String::valueOf).collect(Collectors.toList());
                }
            }
        } catch (Exception ignored) {
            // fallback to delimiter split
        }
        return java.util.Arrays.stream(content.split("[,，\\n\\r\\t;；|]"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toList());
    }

    // ── 内部工具 ───────────────────────────────────────────────────────────────

    private void block(String type, String level, String modelCode, Long assetId, String reason) {
        RiskEvent event = new RiskEvent();
        try {
            event.setCompanyId(companyScopeService.requireCompanyId());
        } catch (Exception ignored) {
            event.setCompanyId(1L);
        }
        event.setType(type);
        event.setLevel(level);
        event.setStatus("open");
        event.setProcessLog("model:" + (modelCode == null ? "unknown" : modelCode)
                + " | asset:" + (assetId == null ? "none" : assetId)
                + " | " + reason);
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        riskEventService.save(event);
    }

    private boolean isEnabled(String status) {
        return status != null && "enabled".equalsIgnoreCase(status.trim());
    }

    private boolean isHighRisk(String riskLevel) {
        if (riskLevel == null) return false;
        String value = riskLevel.trim().toLowerCase(Locale.ROOT);
        return value.contains("high") || value.contains("critical") || value.contains("高");
    }

    private boolean isMediumRisk(String riskLevel) {
        if (riskLevel == null) return false;
        String value = riskLevel.trim().toLowerCase(Locale.ROOT);
        return value.contains("medium") || value.contains("中");
    }

    private boolean isHighSensitivity(String sensitivityLevel) {
        if (sensitivityLevel == null) return false;
        String value = sensitivityLevel.trim().toLowerCase(Locale.ROOT);
        return value.contains("high") || value.contains("critical") || value.contains("高");
    }
}
