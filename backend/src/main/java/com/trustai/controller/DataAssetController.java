package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.dto.DataAssetDetailDto;
import com.trustai.entity.DataAsset;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CurrentUserService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.DataAssetService;
import com.trustai.utils.AssetContentExtractor;
import com.trustai.utils.R;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-asset")
@Validated
public class DataAssetController {
    @Autowired private DataAssetService dataAssetService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private AssetContentExtractor assetContentExtractor;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<List<DataAsset>> list(@RequestParam(required = false) String name) {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            return R.ok(buildDemoAssets(currentUser.getCompanyId(), name));
        }
        QueryWrapper<DataAsset> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        if (name != null && !name.isEmpty()) qw.like("name", name);
        List<DataAsset> assets = dataAssetService.list(qw);
        assets.forEach(this::hydrateReadableDescription);
        return R.ok(assets);
    }

    @PostMapping("/register")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> register(@RequestBody @Validated DataAssetReq asset) {
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<DataAsset> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String assetName,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String sensitivityLevel,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Long ownerId
    ) {
        if (file == null || file.isEmpty()) {
            throw new BizException(40000, "请上传需要治理的数据文件");
        }
        User currentUser = currentUserService.requireCurrentUser();
        String storedPath = storeGovernanceFile(file, currentUser.getUsername());
        String preview = assetContentExtractor.extractPreview(file);

        DataAsset entity = new DataAsset();
        entity.setName(StringUtils.hasText(assetName) ? assetName : deriveName(file));
        entity.setCompanyId(companyScopeService.requireCompanyId());
        entity.setType(StringUtils.hasText(type) ? type : detectType(file.getOriginalFilename()));
        entity.setSensitivityLevel(StringUtils.hasText(sensitivityLevel) ? sensitivityLevel : "medium");
        entity.setOwnerId(resolveOwnerId(ownerId));
        entity.setLocation(storedPath);
        entity.setDescription(buildUploadDescription(description, currentUser, preview));
        entity.setUpdateTime(new Date());

        return R.ok(dataAssetService.register(entity));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<DataAssetDetailDto> detail(@PathVariable Long id) {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            DataAsset demo = buildDemoAssets(currentUser.getCompanyId(), null).stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElse(null);
            if (demo == null) {
                throw new BizException(40400, "演示数据资产不存在");
            }
            DataAssetDetailDto dto = new DataAssetDetailDto();
            dto.setId(demo.getId());
            dto.setName(demo.getName());
            dto.setType(demo.getType());
            dto.setSensitivityLevel(demo.getSensitivityLevel());
            dto.setLocation(demo.getLocation());
            dto.setDescription(demo.getDescription());
            dto.setDiscoveryTime(demo.getCreateTime());
            dto.setOwnerId(currentUser.getId());
            dto.setCreateTime(demo.getCreateTime());
            dto.setUpdateTime(demo.getUpdateTime());
            return R.ok(dto);
        }
        DataAsset scoped = dataAssetService.getOne(companyScopeService.withCompany(new QueryWrapper<DataAsset>()).eq("id", id));
        if (scoped == null) {
            throw new BizException(40400, "数据资产不存在或不在当前公司");
        }
        DataAssetDetailDto detail = dataAssetService.detailWithCalls(id);
        hydrateReadableDescription(detail);
        return R.ok(detail);
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN')")
    public R<?> update(@RequestBody @Validated DataAssetUpdateReq asset) {
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

    private boolean isDemoAccount(User user) {
        return user != null && "demo".equalsIgnoreCase(user.getAccountType());
    }

    private List<DataAsset> buildDemoAssets(Long companyId, String keyword) {
        List<DataAsset> seeded = List.of(
            demoAsset(90001L, companyId, "客户主数据-演示", "database", "high", "demo://crm/customer_master", "包含姓名、手机号、证件号等字段，用于演示脱敏命中"),
            demoAsset(90002L, companyId, "订单交易流-演示", "stream", "medium", "demo://orders/realtime", "近24小时交易流水，展示风险监测与审计追踪"),
            demoAsset(90003L, companyId, "员工通讯录-演示", "file", "medium", "demo://hr/address-book.xlsx", "用于演示共享审批与最小权限授权"),
            demoAsset(90004L, companyId, "营销线索池-演示", "api", "high", "demo://growth/leads", "对接外部线索系统的API模拟数据"),
            demoAsset(90005L, companyId, "匿名行为画像-演示", "lakehouse", "low", "demo://ai/behavior-profile", "用于演示模型调用成本和风险评级场景")
        );
        if (!StringUtils.hasText(keyword)) {
            return seeded;
        }
        return seeded.stream().filter(item -> item.getName() != null && item.getName().contains(keyword)).toList();
    }

    private DataAsset demoAsset(Long id, Long companyId, String name, String type, String sensitivity, String location, String description) {
        DataAsset asset = new DataAsset();
        asset.setId(id);
        asset.setCompanyId(companyId == null ? 1L : companyId);
        asset.setName(name);
        asset.setType(type);
        asset.setSensitivityLevel(sensitivity);
        asset.setLocation(location);
        asset.setDescription(description);
        asset.setCreateTime(new Date());
        asset.setUpdateTime(new Date());
        return asset;
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
}
