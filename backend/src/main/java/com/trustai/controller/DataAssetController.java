package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.dto.DataAssetDetailDto;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.DataAsset;
import com.trustai.entity.PrivacyImpactAssessment;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AiCallAuditService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.DataAssetService;
import com.trustai.service.PrivacyEventService;
import com.trustai.service.PrivacyImpactAssessmentService;
import com.trustai.service.RiskEventService;
import com.trustai.utils.AssetContentExtractor;
import com.trustai.utils.R;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data-asset")
@Validated
public class DataAssetController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private DataAssetService dataAssetService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private AssetContentExtractor assetContentExtractor;
    @Autowired private PrivacyImpactAssessmentService privacyImpactAssessmentService;
    @Autowired private AiCallAuditService aiCallAuditService;
    @Autowired private RiskEventService riskEventService;
    @Autowired private PrivacyEventService privacyEventService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','SECOPS')")
    public R<List<DataAsset>> list(@RequestParam(required = false) String name) {
        currentUserService.requireAnyRole("ADMIN", "DATA_ADMIN", "SECOPS");
        QueryWrapper<DataAsset> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        if (name != null && !name.isEmpty()) qw.like("name", name);
        List<DataAsset> assets = dataAssetService.list(qw);
        assets.forEach(this::hydrateReadableDescription);
        attachLatestAssessment(assets);
        return R.ok(assets);
    }

    @PostMapping("/register")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> register(@RequestBody @Validated DataAssetReq asset) {
        enforceDataAdminDuty("write");
        DataAsset entity = new DataAsset();
        entity.setName(asset.getName());
        entity.setCompanyId(companyScopeService.requireCompanyId());
        entity.setType(asset.getType());
        entity.setSensitivityLevel(asset.getSensitivityLevel());
        entity.setOwnerId(resolveOwnerId(asset.getOwnerId()));
        entity.setLocation(asset.getLocation());
        entity.setDescription(asset.getDescription());
        dataAssetService.register(entity);
        return R.okMsg("注册成功");
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('data_asset:upload')")
    public R<Map<String, Object>> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String assetName,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String sensitivityLevel,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long ownerId
    ) {
        enforceDataAdminDuty("write");
        if (file == null || file.isEmpty()) {
            throw new BizException(40000, "请上传需要治理的数据文件");
        }
        User currentUser = currentUserService.requireCurrentUser();
        String storedPath = storeGovernanceFile(file, currentUser.getUsername());
        String preview = assetContentExtractor.extractPreview(file);
        String recommendedSensitivity = recommendSensitivityLevel(file.getOriginalFilename(), preview, description);

        DataAsset entity = new DataAsset();
        entity.setName(StringUtils.hasText(assetName) ? assetName : deriveName(file));
        entity.setCompanyId(companyScopeService.requireCompanyId());
        entity.setType(StringUtils.hasText(type) ? type : detectType(file.getOriginalFilename()));
        entity.setSensitivityLevel(StringUtils.hasText(sensitivityLevel) ? sensitivityLevel : recommendedSensitivity);
        entity.setOwnerId(resolveOwnerId(ownerId));
        entity.setLocation(storedPath);
        entity.setDescription(buildUploadDescription(description, currentUser, preview));
        entity.setUpdateTime(new Date());

        DataAsset saved = dataAssetService.register(entity);
        Map<String, Object> payload = new HashMap<>();
        payload.put("asset", saved);
        payload.put("recommendedSensitivityLevel", recommendedSensitivity);
        return R.ok(payload);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','SECOPS')")
    public R<DataAssetDetailDto> detail(@PathVariable Long id) {
        currentUserService.requireAnyRole("ADMIN", "DATA_ADMIN", "SECOPS");
        DataAsset scoped = dataAssetService.getOne(companyScopeService.withCompany(new QueryWrapper<DataAsset>()).eq("id", id));
        if (scoped == null) {
            throw new BizException(40400, "数据资产不存在或不在当前公司");
        }
        DataAssetDetailDto detail = dataAssetService.detailWithCalls(id);
        hydrateReadableDescription(detail);
        attachLatestAssessment(detail);
        return R.ok(detail);
    }

    @PostMapping("/{id}/privacy-assess")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','SECOPS')")
    public R<Map<String, Object>> privacyAssess(@PathVariable Long id,
                                                @RequestBody(required = false) PrivacyAssessReq req) {
        currentUserService.requireAnyRole("ADMIN", "DATA_ADMIN", "SECOPS");
        DataAsset asset = requireScopedAsset(id);
        Long companyId = companyScopeService.requireCompanyId();
        User current = currentUserService.requireCurrentUser();
        String framework = req == null || !StringUtils.hasText(req.getFramework()) ? "PIPL" : req.getFramework().trim().toUpperCase();

        Date from = new Date(System.currentTimeMillis() - 30L * 24L * 3600_000L);
        long usageCalls = aiCallAuditService.count(new QueryWrapper<AiCallLog>()
            .eq("company_id", companyId)
            .eq("data_asset_id", asset.getId())
            .ge("create_time", from));
        long riskEvents = riskEventService.count(new QueryWrapper<RiskEvent>()
            .eq("company_id", companyId)
            .in("status", List.of("open", "pending", "processing")));
        long privacyEvents = privacyEventService.count(new QueryWrapper<PrivacyEvent>()
            .eq("company_id", companyId)
            .ge("event_time", from));

        AssessmentScore score = calculateAssessment(asset, usageCalls, riskEvents, privacyEvents);

        PrivacyImpactAssessment record = new PrivacyImpactAssessment();
        record.setCompanyId(companyId);
        record.setAssetId(asset.getId());
        record.setFramework(framework);
        record.setImpactScore(score.score());
        record.setRiskLevel(score.level());
        record.setRiskFactorsJson(toJson(score.factors()));
        record.setAssessedBy(current.getId());
        record.setCreateTime(new Date());
        record.setUpdateTime(new Date());
        privacyImpactAssessmentService.save(record);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", record.getId());
        payload.put("assetId", record.getAssetId());
        payload.put("framework", record.getFramework());
        payload.put("impactScore", record.getImpactScore());
        payload.put("riskLevel", record.getRiskLevel());
        payload.put("riskFactors", score.factors());
        payload.put("updatedAt", record.getCreateTime());
        return R.ok(payload);
    }

    @GetMapping("/{id}/privacy-assess/latest")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','SECOPS')")
    public R<Map<String, Object>> latestPrivacyAssess(@PathVariable Long id) {
        currentUserService.requireAnyRole("ADMIN", "DATA_ADMIN", "SECOPS");
        DataAsset asset = requireScopedAsset(id);
        PrivacyImpactAssessment latest = latestAssessment(asset.getId());
        if (latest == null) {
            return R.ok(Map.of("assetId", id, "exists", false));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exists", true);
        payload.put("assetId", asset.getId());
        payload.put("framework", latest.getFramework());
        payload.put("impactScore", latest.getImpactScore());
        payload.put("riskLevel", latest.getRiskLevel());
        payload.put("riskFactors", latest.getRiskFactorsJson());
        payload.put("updatedAt", latest.getCreateTime());
        return R.ok(payload);
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> update(@RequestBody @Validated DataAssetUpdateReq asset) {
        enforceDataAdminDuty("write");
        DataAsset scoped = dataAssetService.getOne(companyScopeService.withCompany(new QueryWrapper<DataAsset>()).eq("id", asset.getId()));
        if (scoped == null) {
            throw new BizException(40400, "数据资产不存在或不在当前公司");
        }
        DataAsset entity = new DataAsset();
        entity.setId(asset.getId());
        entity.setCompanyId(companyScopeService.requireCompanyId());
        entity.setName(asset.getName());
        entity.setType(asset.getType());
        entity.setSensitivityLevel(asset.getSensitivityLevel());
        entity.setOwnerId(resolveOwnerId(asset.getOwnerId()));
        entity.setLocation(asset.getLocation());
        entity.setDescription(asset.getDescription());
        dataAssetService.updateById(entity);
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        enforceDataAdminDuty("delete");
        DataAsset scoped = dataAssetService.getOne(companyScopeService.withCompany(new QueryWrapper<DataAsset>()).eq("id", req.getId()));
        if (scoped == null) {
            throw new BizException(40400, "数据资产不存在或不在当前公司");
        }
        dataAssetService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    private Long resolveOwnerId(Long ownerId) {
        if (ownerId != null) {
            return ownerId;
        }
        return currentUserService.requireCurrentUser().getId();
    }

    private void enforceDataAdminDuty(String action) {
        User current = currentUserService.requireCurrentUser();
        if (!currentUserService.hasRole("DATA_ADMIN")) {
            return;
        }
        String username = current.getUsername() == null ? "" : current.getUsername().trim().toLowerCase();
        if ("dataadmin_3".equals(username)) {
            throw new BizException(40300, "数据管理员三号为审批岗，不可执行资产变更");
        }
        if ("dataadmin_2".equals(username) && "delete".equals(action)) {
            throw new BizException(40300, "数据管理员二号不可删除资产，仅可维护与上传");
        }
    }

    private String displayName(User user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private String deriveName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            return "治理数据上传";
        }
        int lastDot = original.lastIndexOf('.');
        return lastDot > 0 ? original.substring(0, lastDot) : original;
    }

    private String detectType(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "file";
        }
        String lower = originalFilename.toLowerCase();
        if (lower.endsWith(".csv") || lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "table";
        }
        if (lower.endsWith(".json") || lower.endsWith(".xml")) {
            return "api";
        }
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")) {
            return "document";
        }
        return "file";
    }

    private String recommendSensitivityLevel(String originalFilename, String preview, String customDescription) {
        String combined = String.join(" ",
            StringUtils.hasText(originalFilename) ? originalFilename : "",
            StringUtils.hasText(preview) ? preview : "",
            StringUtils.hasText(customDescription) ? customDescription : ""
        ).toLowerCase();

        if (combined.matches(".*(身份证|id ?card|银行卡|bank ?card|薪资|工资|财务|税号|合同|手机号|phone|email|住址|biometric).*")) {
            return "critical";
        }
        if (combined.matches(".*(客户|customer|订单|order|交易|payment|会话|chat|日志|log|行为|profile|组织|hr|人事).*")) {
            return "high";
        }
        if (combined.matches(".*(研发|代码|repo|git|模型|train|sample|prompt|api|接口).*")) {
            return "medium";
        }
        return "low";
    }

    private String buildUploadDescription(String description, User currentUser, String preview) {
        if (StringUtils.hasText(description)) {
            return description;
        }
        String prefix = "由 " + displayName(currentUser) + " 上传";
        if (StringUtils.hasText(preview)) {
            return prefix + "，已识别内容摘要：" + preview;
        }
        return prefix + "，已进入治理扫描队列";
    }

    private void hydrateReadableDescription(DataAsset asset) {
        if (asset == null || !StringUtils.hasText(asset.getLocation())) {
            return;
        }
        String preview = assetContentExtractor.extractPreview(asset.getLocation());
        if (!StringUtils.hasText(preview)) {
            return;
        }
        if (!StringUtils.hasText(asset.getDescription()) || !asset.getDescription().contains("已识别内容摘要")) {
            asset.setDescription("已识别内容摘要：" + preview);
        }
    }

    private void hydrateReadableDescription(DataAssetDetailDto asset) {
        if (asset == null || !StringUtils.hasText(asset.getLocation())) {
            return;
        }
        String preview = assetContentExtractor.extractPreview(asset.getLocation());
        if (!StringUtils.hasText(preview)) {
            return;
        }
        if (!StringUtils.hasText(asset.getDescription()) || !asset.getDescription().contains("已识别内容摘要")) {
            asset.setDescription("已识别内容摘要：" + preview);
        }
    }

    private DataAsset requireScopedAsset(Long id) {
        DataAsset scoped = dataAssetService.getOne(companyScopeService.withCompany(new QueryWrapper<DataAsset>()).eq("id", id));
        if (scoped == null) {
            throw new BizException(40400, "数据资产不存在或不在当前公司");
        }
        return scoped;
    }

    private void attachLatestAssessment(List<DataAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        List<Long> assetIds = assets.stream().map(DataAsset::getId).filter(v -> v != null).collect(Collectors.toList());
        if (assetIds.isEmpty()) {
            return;
        }
        Map<Long, PrivacyImpactAssessment> latestMap = latestAssessments(assetIds);
        for (DataAsset asset : assets) {
            PrivacyImpactAssessment assessment = latestMap.get(asset.getId());
            if (assessment == null) {
                continue;
            }
            asset.setDiaScore(assessment.getImpactScore());
            asset.setDiaRiskLevel(assessment.getRiskLevel());
            asset.setDiaFramework(assessment.getFramework());
            asset.setDiaUpdatedAt(assessment.getCreateTime());
        }
    }

    private void attachLatestAssessment(DataAssetDetailDto asset) {
        if (asset == null || asset.getId() == null) {
            return;
        }
        PrivacyImpactAssessment latest = latestAssessment(asset.getId());
        if (latest == null) {
            return;
        }
        asset.setDiaScore(latest.getImpactScore());
        asset.setDiaRiskLevel(latest.getRiskLevel());
        asset.setDiaFramework(latest.getFramework());
        asset.setDiaUpdatedAt(latest.getCreateTime());
    }

    private PrivacyImpactAssessment latestAssessment(Long assetId) {
        if (assetId == null) {
            return null;
        }
        List<PrivacyImpactAssessment> rows = privacyImpactAssessmentService.list(new QueryWrapper<PrivacyImpactAssessment>()
            .eq("company_id", companyScopeService.requireCompanyId())
            .eq("asset_id", assetId)
            .orderByDesc("create_time")
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<Long, PrivacyImpactAssessment> latestAssessments(List<Long> assetIds) {
        List<PrivacyImpactAssessment> rows = privacyImpactAssessmentService.list(new QueryWrapper<PrivacyImpactAssessment>()
            .eq("company_id", companyScopeService.requireCompanyId())
            .in("asset_id", assetIds)
            .orderByDesc("create_time")
            .last("limit 1000"));
        Map<Long, PrivacyImpactAssessment> latest = new LinkedHashMap<>();
        for (PrivacyImpactAssessment row : rows) {
            if (row.getAssetId() == null || latest.containsKey(row.getAssetId())) {
                continue;
            }
            latest.put(row.getAssetId(), row);
        }
        return latest;
    }

    private AssessmentScore calculateAssessment(DataAsset asset, long usageCalls, long openRiskEvents, long privacyEvents) {
        int sensitivity = switch (String.valueOf(asset.getSensitivityLevel() == null ? "" : asset.getSensitivityLevel()).toLowerCase()) {
            case "critical", "受限" -> 45;
            case "high", "敏感" -> 34;
            case "medium", "内部" -> 22;
            default -> 10;
        };
        int usage = Math.min(20, (int) Math.round(Math.min(usageCalls, 60) / 60.0 * 20));
        int governance = Math.min(18, (int) Math.round(Math.min(openRiskEvents, 20) / 20.0 * 18));
        int privacy = Math.min(12, (int) Math.round(Math.min(privacyEvents, 50) / 50.0 * 12));
        int location = String.valueOf(asset.getLocation() == null ? "" : asset.getLocation()).toLowerCase().contains("http") ? 5 : 2;

        int total = Math.min(100, sensitivity + usage + governance + privacy + location);
        String level = total >= 70 ? "high" : (total >= 40 ? "medium" : "low");

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("sensitivity", sensitivity);
        factors.put("usageExposure", usage);
        factors.put("governancePressure", governance);
        factors.put("privacyAlerts", privacy);
        factors.put("locationRisk", location);
        factors.put("inputs", Map.of(
            "usageCalls30d", usageCalls,
            "openRiskEvents", openRiskEvents,
            "privacyEvents30d", privacyEvents,
            "assetType", String.valueOf(asset.getType()),
            "sensitivityLevel", String.valueOf(asset.getSensitivityLevel())
        ));
        return new AssessmentScore(total, level, factors);
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String storeGovernanceFile(MultipartFile file, String username) {
        try {
            String original = file.getOriginalFilename();
            String ext = original != null && original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
            Path dir = Paths.get("uploads", "governance-data", username == null ? "anonymous" : username);
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + ext);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString().replace('\\', '/');
        } catch (IOException e) {
            throw new BizException(50000, "数据文件上传失败: " + e.getMessage());
        }
    }

    public static class IdReq { @jakarta.validation.constraints.NotNull private Long id; public Long getId(){return id;} public void setId(Long id){this.id=id;} }
    public static class DataAssetReq {
        @NotBlank private String name;
        @NotBlank private String type;
        private String sensitivityLevel;
        private Long ownerId;
        private String location;
        private String description;
        public String getName(){return name;} public void setName(String v){name=v;}
        public String getType(){return type;} public void setType(String v){type=v;}
        public String getSensitivityLevel(){return sensitivityLevel;} public void setSensitivityLevel(String v){sensitivityLevel=v;}
        public Long getOwnerId(){return ownerId;} public void setOwnerId(Long v){ownerId=v;}
        public String getLocation(){return location;} public void setLocation(String v){location=v;}
        public String getDescription(){return description;} public void setDescription(String v){description=v;}
    }
    public static class DataAssetUpdateReq extends DataAssetReq { @jakarta.validation.constraints.NotNull private Long id; public Long getId(){return id;} public void setId(Long v){id=v;} }
    public static class PrivacyAssessReq {
        private String framework;
        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }
    }

    private record AssessmentScore(int score, String level, Map<String, Object> factors) {}
}
