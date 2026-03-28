package com.trustai.controller;

import com.trustai.config.CrossSiteGuardService;
import com.trustai.utils.R;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/cross-site")
public class SecurityGuardController {

    private final CrossSiteGuardService crossSiteGuardService;

    public SecurityGuardController(CrossSiteGuardService crossSiteGuardService) {
        this.crossSiteGuardService = crossSiteGuardService;
    }

    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(crossSiteGuardService.snapshot());
    }
}
