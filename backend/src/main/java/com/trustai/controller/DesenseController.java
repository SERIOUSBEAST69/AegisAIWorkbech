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
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/desense")
@Validated
public class DesenseController {

    @Autowired
    private DesensitizeRuleService ruleService;
    @Autowired
    private RecommendationService recommendationService;

    @GetMapping("/rules")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','DATA_ADMIN','DATA_ADMIN_MAINTAINER')")
    public R<List<DesensitizeRule>> rules() {
        return R.ok(ruleService.list());
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> save(@RequestBody DesensitizeRule rule) {
        if (rule.getId() == null) ruleService.save(rule); else ruleService.updateById(rule);
        return R.ok(rule);
    }

    @PostMapping("/recommend")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','DATA_ADMIN','DATA_ADMIN_MAINTAINER')")
    public R<List<DesenseRecommendationDto>> recommend(@RequestBody RecommendReq req) {
        List<DesenseRecommendationDto> data = recommendationService.recommend(req.getDataCategory(), req.getUserRole(), req.getSensitivityLevel());
        return R.ok(data);
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        ruleService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    @PostMapping("/preview")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','DATA_ADMIN','DATA_ADMIN_MAINTAINER')")
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
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
        String trimmed = sample.trim();
        int leftKeep = 2;
        int rightKeep = 2;
        if (trimmed.matches("^\\d{17}[\\dXx]$")) {
            leftKeep = 6;
            rightKeep = 4;
        } else if (trimmed.matches("^1\\d{10}$")) {
            leftKeep = 3;
            rightKeep = 4;
        } else if (trimmed.matches("^[^@\\s]{3,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            leftKeep = 2;
            rightKeep = 2;
        }
        if (sample.length() <= leftKeep + rightKeep) return mask.repeat(sample.length());
        StringBuilder sb = new StringBuilder();
        sb.append(sample, 0, leftKeep);
        for (int i = leftKeep; i < sample.length() - rightKeep; i++) sb.append(mask.charAt(0));
        sb.append(sample, sample.length() - rightKeep, sample.length());
        return sb.toString();
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
