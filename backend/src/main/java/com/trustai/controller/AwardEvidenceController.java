package com.trustai.controller;

import com.trustai.service.AwardEvidenceService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.ExternalAnchorService;
import com.trustai.service.NationalAwardReadinessService;
import com.trustai.utils.R;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/award")
public class AwardEvidenceController {

    private final AwardEvidenceService awardEvidenceService;
    private final ExternalAnchorService externalAnchorService;
    private final CompanyScopeService companyScopeService;
    private final NationalAwardReadinessService nationalAwardReadinessService;

    public AwardEvidenceController(AwardEvidenceService awardEvidenceService,
                                   ExternalAnchorService externalAnchorService,
                                   CompanyScopeService companyScopeService,
                                   NationalAwardReadinessService nationalAwardReadinessService) {
        this.awardEvidenceService = awardEvidenceService;
        this.externalAnchorService = externalAnchorService;
        this.companyScopeService = companyScopeService;
        this.nationalAwardReadinessService = nationalAwardReadinessService;
    }

    @GetMapping("/experiment-report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> experimentReport(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baselineFrom,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baselineTo,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate currentFrom,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate currentTo
    ) {
        return R.ok(awardEvidenceService.buildExperimentReport(baselineFrom, baselineTo, currentFrom, currentTo));
    }

    @PostMapping("/compliance-evidence/generate")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> generateComplianceEvidence(@RequestBody(required = false) EvidenceRangeReq req) {
        LocalDate to = req != null && req.getTo() != null ? req.getTo() : LocalDate.now();
        LocalDate from = req != null && req.getFrom() != null ? req.getFrom() : to.minusDays(6);
        return R.ok(awardEvidenceService.generateComplianceEvidence(from, to));
    }

    @GetMapping("/compliance-evidence/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<List<Map<String, Object>>> complianceEvidenceList(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(awardEvidenceService.listComplianceEvidence(limit));
    }

    @PostMapping("/reliability/drill/run")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> runReliabilityDrill(@RequestBody(required = false) ReliabilityReq req) {
        String scenario = req == null ? null : req.getScenario();
        String targetPath = req == null ? null : req.getTargetPath();
        String injectPath = req == null ? null : req.getInjectPath();
        int probeCount = req == null || req.getProbeCount() == null ? 3 : req.getProbeCount();
        return R.ok(awardEvidenceService.runReliabilityDrill(scenario, targetPath, injectPath, probeCount));
    }

    @GetMapping("/reliability/drill/history")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<List<Map<String, Object>>> reliabilityHistory(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(awardEvidenceService.listReliabilityDrills(limit));
    }

    @GetMapping("/summary")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> summary() {
        return R.ok(awardEvidenceService.summary());
    }

    @GetMapping("/compliance-mapping")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> complianceMapping() {
        return R.ok(awardEvidenceService.privacyComplianceMapping());
    }

    @PostMapping("/audit-hash-chain/build")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> buildAuditHashChain(@RequestBody(required = false) EvidenceRangeReq req) {
        LocalDate to = req != null && req.getTo() != null ? req.getTo() : LocalDate.now();
        LocalDate from = req != null && req.getFrom() != null ? req.getFrom() : to.minusDays(6);
        return R.ok(awardEvidenceService.buildAuditHashChain(from, to));
    }

    @GetMapping("/industry-spec-comparison")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> industrySpecComparison() {
        return R.ok(awardEvidenceService.industrySpecComparison());
    }

    @PostMapping("/export")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> exportEvidence(@RequestBody(required = false) ExportReq req) {
        LocalDate to = req != null && req.getTo() != null ? req.getTo() : LocalDate.now();
        LocalDate from = req != null && req.getFrom() != null ? req.getFrom() : to.minusDays(6);
        boolean includePdf = req == null || req.getIncludePdf() == null || req.getIncludePdf();
        boolean includeJson = req == null || req.getIncludeJson() == null || req.getIncludeJson();
        return R.ok(awardEvidenceService.exportEvidencePackage(from, to, includePdf, includeJson));
    }

    @GetMapping("/zero-trust")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> zeroTrust() {
        return R.ok(awardEvidenceService.zeroTrustAssessment());
    }

    @GetMapping("/threat-inventory")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> threatInventory() {
        return R.ok(awardEvidenceService.generateThreatInventory());
    }

    @GetMapping("/adversarial-replay-report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> adversarialReplay(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(awardEvidenceService.generateAdversarialReplayReport(limit));
    }

    @GetMapping("/innovation-report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> innovationReport(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baselineFrom,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate baselineTo,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate currentFrom,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate currentTo
    ) {
        return R.ok(awardEvidenceService.innovationComparisonReport(baselineFrom, baselineTo, currentFrom, currentTo));
    }

    @GetMapping("/external-anchor/latest")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> latestExternalAnchors(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(externalAnchorService.latestAnchors(companyScopeService.requireCompanyId(), limit));
    }

    @GetMapping("/external-anchor/verify")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> verifyExternalAnchor(@RequestParam String payloadHash) {
        return R.ok(externalAnchorService.verifyByPayloadHash(companyScopeService.requireCompanyId(), payloadHash));
    }

    @GetMapping("/evaluation/fixed-package")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> fixedEvaluationPackage() {
        return R.ok(awardEvidenceService.buildFixedEvaluationPackage());
    }

    @GetMapping("/readiness/report")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> readinessReport() {
        return R.ok(nationalAwardReadinessService.readinessReport());
    }

    @PostMapping("/readiness/auto-remediate")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> autoRemediate(@RequestBody(required = false) AutoRemediateReq req) {
        boolean dryRun = req == null || req.getDryRun() == null || req.getDryRun();
        return R.ok(nationalAwardReadinessService.runAutoRemediationNow(dryRun));
    }

    @GetMapping("/readiness/auto-remediate/last")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> lastAutoRemediate() {
        return R.ok(nationalAwardReadinessService.lastAutopilotRun());
    }

    public static class EvidenceRangeReq {
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate from;

        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private LocalDate to;

        public LocalDate getFrom() {
            return from;
        }

        public void setFrom(LocalDate from) {
            this.from = from;
        }

        public LocalDate getTo() {
            return to;
        }

        public void setTo(LocalDate to) {
            this.to = to;
        }
    }

    public static class ReliabilityReq {
        private String scenario;
        private String targetPath;
        private String injectPath;
        private Integer probeCount;

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }

        public String getInjectPath() {
            return injectPath;
        }

        public void setInjectPath(String injectPath) {
            this.injectPath = injectPath;
        }

        public Integer getProbeCount() {
            return probeCount;
        }

        public void setProbeCount(Integer probeCount) {
            this.probeCount = probeCount;
        }
    }

    public static class ExportReq extends EvidenceRangeReq {
        private Boolean includePdf;
        private Boolean includeJson;

        public Boolean getIncludePdf() {
            return includePdf;
        }

        public void setIncludePdf(Boolean includePdf) {
            this.includePdf = includePdf;
        }

        public Boolean getIncludeJson() {
            return includeJson;
        }

        public void setIncludeJson(Boolean includeJson) {
            this.includeJson = includeJson;
        }
    }

    public static class AutoRemediateReq {
        private Boolean dryRun;

        public Boolean getDryRun() {
            return dryRun;
        }

        public void setDryRun(Boolean dryRun) {
            this.dryRun = dryRun;
        }
    }
}
