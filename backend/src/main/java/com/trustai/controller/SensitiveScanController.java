package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.dto.SensitiveScanReport;
import com.trustai.entity.SensitiveScanTask;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SensitiveScanEngine;
import com.trustai.service.SensitiveScanTaskService;
import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/sensitive-scan")
@Validated
@Slf4j
public class SensitiveScanController {

    private static final String SOURCE_TYPE_FILE = "file";
    private static final long MAX_UPLOAD_SIZE = 200L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("xlsx", "csv", "json", "db", "parquet");

    @Autowired
    private SensitiveScanTaskService taskService;
    @Autowired
    private SensitiveScanEngine scanEngine;
    @Autowired
    private com.trustai.utils.AssetContentExtractor assetContentExtractor;
    @Autowired
    private CurrentUserService currentUserService;
    @Autowired
    private CompanyScopeService companyScopeService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PostMapping("/create")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<SensitiveScanTask> create(@RequestBody @Validated CreateReq req, HttpServletRequest httpRequest) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        SensitiveScanTask task = new SensitiveScanTask();
        task.setAssetId(req.getAssetId());
        task.setCompanyId(companyId);
        task.setUserId(currentUser.getId());
        task.setSourceType(req.getSourceType());
        task.setSourcePath(req.getSourcePath());
        task.setTraceJson(buildTraceJson(currentUser, companyId, httpRequest));
        task.setStatus("pending");
        task.setCreateTime(new Date());
        taskService.save(task);
        return R.ok(task);
    }

    @PostMapping("/upload")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return R.error(40000, "上传文件为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.trim().isEmpty()) {
            return R.error(40000, "文件名无效");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            return R.error(40000, "文件大小超过限制(200MB)");
        }

        String cleanName = Paths.get(originalName).getFileName().toString().replaceAll("\\s+", "_");
        int dot = cleanName.lastIndexOf('.');
        if (dot < 0 || dot == cleanName.length() - 1) {
            return R.error(40000, "文件后缀不合法");
        }
        String ext = cleanName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return R.error(40000, "仅支持 .xlsx/.csv/.json/.db/.parquet 文件");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + cleanName;
        Path uploadDir = Paths.get("data", "upload");
        Path absolute = uploadDir.resolve(storedName).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), absolute, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Sensitive scan file upload failed", ex);
            return R.error(50000, "上传失败，请稍后重试");
        }

        String sourcePath = "/data/upload/" + storedName;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", SOURCE_TYPE_FILE);
        payload.put("sourcePath", sourcePath);
        payload.put("fileName", cleanName);
        payload.put("size", file.getSize());
        payload.put("deviceIp", resolveClientIp(request));
        return R.ok(payload);
    }

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String status) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean ownerScope = currentUserService.hasRole("BUSINESS_OWNER");
        QueryWrapper<SensitiveScanTask> qw = new QueryWrapper<SensitiveScanTask>()
            .eq("company_id", companyId)
            .eq(ownerScope, "user_id", currentUser.getId());
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        qw.orderByDesc("update_time");
        try {
            int safePage = Math.max(1, page);
            int safePageSize = Math.max(1, Math.min(100, pageSize));
            Page<SensitiveScanTask> result = taskService.page(new Page<>(safePage, safePageSize), qw);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("current", result.getCurrent());
            payload.put("pages", result.getPages());
            payload.put("total", result.getTotal());
            payload.put("list", result.getRecords());
            return R.ok(payload);
        } catch (Exception ex) {
            log.warn("SensitiveScan list degraded due to schema mismatch: {}", ex.getMessage());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("current", Math.max(1, page));
            payload.put("pages", 0);
            payload.put("total", 0);
            payload.put("list", List.of());
            return R.ok(payload);
        }
    }

    @PostMapping("/run")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<SensitiveScanTask> run(@RequestBody @Validated IdReq req) {
        SensitiveScanTask task = requireScopedTask(req.getId());
        if (task == null) return R.error(40000, "任务不存在");

        task.setStatus("running");
        task.setUpdateTime(new Date());
        taskService.updateById(task);

        // 优先尝试从文件路径中提取真实内容用于 BERT 扫描；
        // 若无法提取（非文件路径或文件不存在），则以路径字符串本身作为样本
        List<String> samples;
        String sourcePath = task.getSourcePath();
        if (sourcePath != null && !sourcePath.isEmpty() && SOURCE_TYPE_FILE.equals(task.getSourceType())) {
            String resolvedSourcePath = resolveScanSourcePath(sourcePath);
            String content = assetContentExtractor.extractPreview(resolvedSourcePath);
            if (content != null && !content.isEmpty()) {
                samples = List.of(content);
            } else {
                task.setStatus("failed");
                task.setUpdateTime(new Date());
                taskService.updateById(task);
                log.warn("SensitiveScan task {}: could not extract text from '{}' (resolved='{}')", task.getId(), sourcePath, resolvedSourcePath);
                return R.error(40000, "无法从上传文件中提取可识别文本，请上传文本类文件或检查文件内容编码");
            }
        } else {
            samples = (sourcePath != null && !sourcePath.isEmpty()) ? List.of(sourcePath) : List.of("待扫描文本样例");
        }

        SensitiveScanReport report;
        try {
            report = scanEngine.scan(samples);
        } catch (Exception ex) {
            log.error("SensitiveScan task {} execution failed: {}", task.getId(), ex.getMessage());
            task.setStatus("failed");
            task.setUpdateTime(new Date());
            taskService.updateById(task);
            return R.error(50000, "敏感扫描执行失败，请检查推理服务与模型状态");
        }
        try {
            task.setReportData(MAPPER.writeValueAsString(report));
        } catch (Exception e) {
            task.setReportData(null);
        }
        task.setSensitiveRatio(report.getSummary() == null ? 0.0 : report.getSummary().getRatio());
        task.setStatus("done");
        task.setReportPath("/reports/task-" + task.getId() + ".json");
        task.setUpdateTime(new Date());
        taskService.updateById(task);
        return R.ok(task);
    }

    private String resolveScanSourcePath(String sourcePath) {
        String normalized = String.valueOf(sourcePath == null ? "" : sourcePath).trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        Path rawPath = Paths.get(normalized);
        if (rawPath.isAbsolute() && Files.exists(rawPath)) {
            return rawPath.toString();
        }
        if (normalized.startsWith("/data/upload/")) {
            String fileName = Paths.get(normalized).getFileName() == null ? "" : Paths.get(normalized).getFileName().toString();
            if (!fileName.isEmpty()) {
                Path localUpload = Paths.get("data", "upload", fileName);
                if (Files.exists(localUpload)) {
                    return localUpload.toString();
                }
            }
        }
        Path relativePath = Paths.get(normalized.startsWith("/") ? normalized.substring(1) : normalized);
        if (Files.exists(relativePath)) {
            return relativePath.toString();
        }
        return normalized;
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<?> report(@PathVariable Long id) {
        SensitiveScanTask task = requireScopedTask(id);
        if (task == null) return R.error(40000, "任务不存在");
        if (task.getReportData() == null) return R.error(40000, "报告未生成");
        return R.ok(task.getReportData());
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        SensitiveScanTask task = requireScopedTask(req.getId());
        if (task == null) return R.error(40000, "任务不存在");
        taskService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    private SensitiveScanTask requireScopedTask(Long id) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean ownerScope = currentUserService.hasRole("BUSINESS_OWNER");
        QueryWrapper<SensitiveScanTask> qw = new QueryWrapper<SensitiveScanTask>()
            .eq("id", id)
            .eq("company_id", companyId)
            .eq(ownerScope, "user_id", currentUser.getId());
        return taskService.getOne(qw);
    }

    private String buildTraceJson(User user, Long companyId, HttpServletRequest request) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("username", user == null ? "-" : user.getUsername());
        trace.put("userId", user == null ? "-" : user.getId());
        trace.put("role", currentUserService.currentRoleCode());
        trace.put("department", user == null ? "-" : user.getDepartment());
        trace.put("position", user == null ? "-" : user.getJobTitle());
        trace.put("companyId", companyId == null ? "-" : companyId);
        trace.put("device", user == null ? "-" : user.getDeviceId());
        trace.put("deviceIp", resolveClientIp(request));
        try {
            return MAPPER.writeValueAsString(trace);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() == null ? "-" : request.getRemoteAddr();
    }

    public static class IdReq { @NotNull private Long id; public Long getId(){return id;} public void setId(Long id){this.id=id;} }
    public static class CreateReq { @NotBlank private String sourceType; @NotBlank private String sourcePath; private Long assetId; public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;} public String getSourcePath(){return sourcePath;} public void setSourcePath(String v){sourcePath=v;} public Long getAssetId(){return assetId;} public void setAssetId(Long assetId){this.assetId=assetId;} }
}
