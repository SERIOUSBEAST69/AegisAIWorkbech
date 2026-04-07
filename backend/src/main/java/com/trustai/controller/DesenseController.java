package com.trustai.controller;

import com.trustai.entity.DesensitizeRule;
import com.trustai.dto.DesenseRecommendationDto;
import com.trustai.service.RecommendationService;
import com.trustai.utils.R;
import com.trustai.service.DesensitizeRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/desense")
@Validated
public class DesenseController {

    private static final Pattern ID_CARD_18 = Pattern.compile("\\b([1-9]\\d{5})(\\d{8})(\\d{3}[0-9Xx])\\b");
    private static final Pattern ID_CARD_15 = Pattern.compile("\\b([1-9]\\d{5})(\\d{5})(\\d{4})\\b");

    @Autowired
    private DesensitizeRuleService ruleService;
    @Autowired
    private RecommendationService recommendationService;

    @GetMapping("/rules")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<DesensitizeRule>> rules() {
        return R.ok(ruleService.list());
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<?> save(@RequestBody DesensitizeRule rule) {
        if (rule.getId() == null) ruleService.save(rule); else ruleService.updateById(rule);
        return R.ok(rule);
    }

    @PostMapping("/recommend")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<DesenseRecommendationDto>> recommend(@RequestBody RecommendReq req) {
        List<DesenseRecommendationDto> data = recommendationService.recommend(req.getDataCategory(), req.getUserRole(), req.getSensitivityLevel());
        return R.ok(data);
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        ruleService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    @PostMapping("/preview")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, String>> preview(@RequestBody PreviewReq req) {
        String source = req.getSample();
        if (source == null) {
            source = req.getText();
        }
        String masked = applyMask(source, req.getMask());
        Map<String, String> map = new HashMap<>();
        map.put("raw", source == null ? "" : source);
        map.put("masked", masked);
        return R.ok(map);
    }

    @PostMapping("/execute")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> execute(@RequestBody ExecuteReq req) {
        String source = req.getSample();
        if (source == null) {
            source = req.getText();
        }
        String masked = applyMask(source, req.getMask());
        Map<String, Object> map = new HashMap<>();
        map.put("raw", source == null ? "" : source);
        map.put("masked", masked);
        map.put("ruleId", req.getRuleId());
        map.put("executedAt", System.currentTimeMillis());
        return R.ok(map);
    }

    private String applyMask(String sample, String mask) {
        if (sample == null) return "";
        if (mask == null || mask.isEmpty()) return sample;
        char maskChar = mask.charAt(0);
        String masked = maskIdCards(sample, maskChar);

        String trimmed = masked.trim();
        int leftKeep = 2;
        int rightKeep = 2;
        if (trimmed.matches("^1\\d{10}$")) {
            leftKeep = 3;
            rightKeep = 4;
        } else if (trimmed.matches("^[^@\\s]{3,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            leftKeep = 2;
            rightKeep = 2;
        } else if (trimmed.matches("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$")
            || trimmed.matches("^[1-9]\\d{14}$")) {
            leftKeep = 6;
            rightKeep = 4;
        }

        if (masked.length() <= leftKeep + rightKeep) return String.valueOf(maskChar).repeat(masked.length());
        StringBuilder sb = new StringBuilder();
        sb.append(masked, 0, leftKeep);
        for (int i = leftKeep; i < masked.length() - rightKeep; i++) sb.append(maskChar);
        sb.append(masked, masked.length() - rightKeep, masked.length());
        return sb.toString();
    }

    private String maskIdCards(String text, char maskChar) {
        Matcher m18 = ID_CARD_18.matcher(text);
        StringBuffer sb18 = new StringBuffer();
        while (m18.find()) {
            String replacement = m18.group(1) + String.valueOf(maskChar).repeat(m18.group(2).length()) + m18.group(3);
            m18.appendReplacement(sb18, replacement);
        }
        m18.appendTail(sb18);

        Matcher m15 = ID_CARD_15.matcher(sb18.toString());
        StringBuffer sb15 = new StringBuffer();
        while (m15.find()) {
            String replacement = m15.group(1) + String.valueOf(maskChar).repeat(m15.group(2).length()) + m15.group(3);
            m15.appendReplacement(sb15, replacement);
        }
        m15.appendTail(sb15);
        return sb15.toString();
    }

    public static class PreviewReq {
        private String sample; private String text; private String mask;
        public String getSample(){return sample;} public void setSample(String s){this.sample=s;}
        public String getText(){return text;} public void setText(String text){this.text=text;}
        public String getMask(){return mask;} public void setMask(String m){this.mask=m;}
    }

    public static class ExecuteReq extends PreviewReq {
        private Long ruleId;
        public Long getRuleId(){return ruleId;}
        public void setRuleId(Long ruleId){this.ruleId=ruleId;}
    }

    public static class RecommendReq {
        private String dataCategory;
        private String userRole;
        private String sensitivityLevel;
        public String getDataCategory(){return dataCategory;}
        public void setDataCategory(String dataCategory){this.dataCategory=dataCategory;}
        public String getUserRole(){return userRole;}
        public void setUserRole(String userRole){this.userRole=userRole;}
        public String getSensitivityLevel(){return sensitivityLevel;}
        public void setSensitivityLevel(String sensitivityLevel){this.sensitivityLevel=sensitivityLevel;}
    }

    public static class IdReq {
        @NotNull private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }
}
