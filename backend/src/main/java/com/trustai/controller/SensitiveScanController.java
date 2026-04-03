package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.dto.SensitiveScanReport;
import com.trustai.entity.SensitiveScanTask;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SensitiveScanEngine;
import com.trustai.service.SensitiveScanTaskService;
import com.trustai.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensitive-scan")
@Validated
@Slf4j
public class SensitiveScanController {

    private static final String SOURCE_TYPE_FILE = "file";

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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<SensitiveScanTask> create(@RequestBody @Validated CreateReq req) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        SensitiveScanTask task = new SensitiveScanTask();
        task.setAssetId(req.getAssetId());
        task.setCompanyId(companyId);
        task.setUserId(currentUser.getId());
        task.setSourceType(req.getSourceType());
        task.setSourcePath(req.getSourcePath());
        task.setTraceJson(buildTraceJson(currentUser, companyId));
        task.setStatus("pending");
        task.setCreateTime(new Date());
        taskService.save(task);
        return R.ok(task);
    }

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','DATA_ADMIN')")
    public R<List<SensitiveScanTask>> list(@RequestParam(required = false) String status) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean dataAdminScope = "DATA_ADMIN".equalsIgnoreCase(currentUserService.currentRoleCode());
        QueryWrapper<SensitiveScanTask> qw = new QueryWrapper<SensitiveScanTask>()
            .eq("company_id", companyId)
            .eq(dataAdminScope, "user_id", currentUser.getId());
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        try {
            return R.ok(taskService.list(qw));
        } catch (Exception ex) {
            log.warn("SensitiveScan list degraded due to schema mismatch: {}", ex.getMessage());
            return R.ok(List.of());
        }
    }

    @PostMapping("/run")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<SensitiveScanTask> run(@RequestBody @Validated IdReq req) {
        SensitiveScanTask task = requireScopedTask(req.getId());
        if (task == null) return R.error(40000, "任务不存在");

        // 优先尝试从文件路径中提取真实内容用于 BERT 扫描；
        // 若无法提取（非文件路径或文件不存在），则以路径字符串本身作为样本
        List<String> samples;
        String sourcePath = task.getSourcePath();
        if (sourcePath != null && !sourcePath.isEmpty() && SOURCE_TYPE_FILE.equals(task.getSourceType())) {
            String content = assetContentExtractor.extractPreview(sourcePath);
            if (content != null && !content.isEmpty()) {
                samples = List.of(content);
            } else {
                log.warn("SensitiveScan task {}: could not extract text from '{}', falling back to path as sample", task.getId(), sourcePath);
                samples = List.of(sourcePath);
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

    @GetMapping("/{id}/report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> report(@PathVariable Long id) {
        SensitiveScanTask task = requireScopedTask(id);
        if (task == null) return R.error(40000, "任务不存在");
        if (task.getReportData() == null) return R.error(40000, "报告未生成");
        return R.ok(task.getReportData());
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        SensitiveScanTask task = requireScopedTask(req.getId());
        if (task == null) return R.error(40000, "任务不存在");
        taskService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    private SensitiveScanTask requireScopedTask(Long id) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean dataAdminScope = "DATA_ADMIN".equalsIgnoreCase(currentUserService.currentRoleCode());
        QueryWrapper<SensitiveScanTask> qw = new QueryWrapper<SensitiveScanTask>()
            .eq("id", id)
            .eq("company_id", companyId)
            .eq(dataAdminScope, "user_id", currentUser.getId());
        return taskService.getOne(qw);
    }

    private String buildTraceJson(User user, Long companyId) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("username", user == null ? "-" : user.getUsername());
        trace.put("userId", user == null ? "-" : user.getId());
        trace.put("role", currentUserService.currentRoleCode());
        trace.put("department", user == null ? "-" : user.getDepartment());
        trace.put("position", user == null ? "-" : user.getJobTitle());
        trace.put("companyId", companyId == null ? "-" : companyId);
        trace.put("device", user == null ? "-" : user.getDeviceId());
        try {
            return MAPPER.writeValueAsString(trace);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public static class IdReq { @NotNull private Long id; public Long getId(){return id;} public void setId(Long id){this.id=id;} }
    public static class CreateReq { @NotBlank private String sourceType; @NotBlank private String sourcePath; private Long assetId; public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;} public String getSourcePath(){return sourcePath;} public void setSourcePath(String v){sourcePath=v;} public Long getAssetId(){return assetId;} public void setAssetId(Long assetId){this.assetId=assetId;} }
}
