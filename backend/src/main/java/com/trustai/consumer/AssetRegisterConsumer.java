package com.trustai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.dto.SensitiveScanReport;
import com.trustai.entity.DataAsset;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SensitiveScanTask;
import com.trustai.service.RiskEventService;
import com.trustai.service.SensitiveScanEngine;
import com.trustai.service.SensitiveScanTaskService;
import com.trustai.utils.AssetContentExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 资产注册事件消费者：监听 {@link RabbitConfig#ASSET_REGISTER_QUEUE}。
 *
 * <p>流程：
 * <ol>
 *   <li>创建扫描任务（status = running）</li>
 *   <li>调用 {@link SensitiveScanEngine} 执行 BERT + 正则扫描</li>
 *   <li>更新任务状态（status = done）并持久化报告</li>
 *   <li>根据敏感占比自动生成风险事件</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssetRegisterConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SensitiveScanTaskService sensitiveScanTaskService;
    private final RiskEventService riskEventService;
    private final SensitiveScanEngine sensitiveScanEngine;
    private final AssetContentExtractor assetContentExtractor;

    @RabbitListener(queues = RabbitConfig.ASSET_REGISTER_QUEUE)
    public void onMessage(String payload) {
        try {
            DataAsset asset = MAPPER.readValue(payload, DataAsset.class);
            runScanAndRecord(asset);
        } catch (Exception e) {
            log.error("Asset register consumer failed", e);
        }
    }

    private void runScanAndRecord(DataAsset asset) {
        Date now = new Date();

        // 1. 创建扫描任务
        SensitiveScanTask task = new SensitiveScanTask();
        task.setAssetId(asset.getId());
        task.setSourceType(asset.getType() == null ? "asset" : asset.getType());
        task.setSourcePath(asset.getLocation() == null ? ("asset-" + asset.getId()) : asset.getLocation());
        task.setStatus("running");
        task.setCreateTime(now);
        sensitiveScanTaskService.save(task);

        // 2. 构造扫描样本
        List<String> samples = new ArrayList<>();
        String contentPreview = assetContentExtractor.extractPreview(asset.getLocation());
        if (StringUtils.hasText(contentPreview)) {
            samples.add(contentPreview);
        }
        if (StringUtils.hasText(asset.getDescription())) {
            samples.add(asset.getDescription());
        }
        if (!StringUtils.hasText(contentPreview) && StringUtils.hasText(asset.getLocation())) {
            samples.add(asset.getLocation());
        }
        if (samples.isEmpty() && StringUtils.hasText(asset.getName())) {
            samples.add(asset.getName());
        }

        // 3. 执行 BERT + 正则扫描
        SensitiveScanReport report = sensitiveScanEngine.scan(samples);
        double ratio = report.getSummary() == null ? 0.0 : report.getSummary().getRatio();

        // 4. 更新任务状态
        try {
            task.setReportData(MAPPER.writeValueAsString(report));
        } catch (Exception ignored) { }
        task.setSensitiveRatio(ratio);
        task.setStatus("done");
        task.setReportPath("/reports/task-" + task.getId() + ".json");
        task.setUpdateTime(Date.from(Instant.now()));
        sensitiveScanTaskService.updateById(task);

        // 5. 自动生成风险事件
        String level = ratio > 60 ? "critical" : (ratio > 30 ? "high" : "medium");
        RiskEvent event = new RiskEvent();
        event.setType("敏感数据扫描");
        event.setLevel(level);
        event.setRelatedLogId(task.getId());
        event.setStatus("open");
        event.setProcessLog("自动创建，敏感占比" + String.format("%.2f", ratio) + "%");
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        riskEventService.save(event);

        log.info("Asset {} scan completed, sensitiveRatio={}, riskLevel={}", asset.getId(), ratio, level);
    }
}
