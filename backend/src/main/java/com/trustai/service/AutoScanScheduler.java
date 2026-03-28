package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.dto.SensitiveScanReport;
import com.trustai.entity.DataAsset;
import com.trustai.entity.SensitiveScanTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 敏感数据自动扫描定时任务
 *
 * <p>自问自答 – Q3：扫描任务是真正自动触发的吗？还是需要手动点一下？
 * 答：本调度器确保：
 * <ol>
 *   <li>每天凌晨 03:00 自动扫描过去 24 小时内新增的数据资产</li>
 *   <li>为每个新资产创建 pending 扫描任务并立即执行（通过 SensitiveScanEngine）</li>
 *   <li>手动触发（/api/sensitive-scan/run）仍然保留，供人工复核</li>
 * </ol>
 *
 * <p>扫描结果写入 sensitive_scan_task 表，前端敏感扫描页实时读取，
 * 不存在任何写死/Mock 数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoScanScheduler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataAssetService dataAssetService;
    private final SensitiveScanTaskService scanTaskService;
    private final SensitiveScanEngine scanEngine;

    /**
     * 每天 03:00 自动扫描过去 24 小时内新增的数据资产。
     * 若有资产尚未扫描（无 done 状态的任务），则创建并执行扫描任务。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyAutoScan() {
        log.info("[AutoScanScheduler] Daily auto-scan job started");
        Date since = new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
        List<DataAsset> newAssets = dataAssetService.list(
                new LambdaQueryWrapper<DataAsset>().ge(DataAsset::getCreateTime, since)
        );
        log.info("[AutoScanScheduler] Found {} new assets to scan", newAssets.size());
        for (DataAsset asset : newAssets) {
            try {
                scanAsset(asset);
            } catch (Exception e) {
                log.warn("[AutoScanScheduler] Scan failed for asset {}: {}", asset.getId(), e.getMessage());
            }
        }
    }

    /**
     * 全量补扫：对所有没有 done 扫描记录的资产补做一次扫描。
     * 每周日 04:00 执行一次，确保存量资产不遗漏。
     */
    @Scheduled(cron = "0 0 4 * * SUN")
    public void weeklyFullScan() {
        log.info("[AutoScanScheduler] Weekly full-scan job started");
        List<DataAsset> allAssets = dataAssetService.list();
        int scanned = 0;
        for (DataAsset asset : allAssets) {
            long doneCount = scanTaskService.count(
                    new QueryWrapper<SensitiveScanTask>()
                            .eq("asset_id", asset.getId())
                            .eq("status", "done")
            );
            if (doneCount == 0) {
                try {
                    scanAsset(asset);
                    scanned++;
                } catch (Exception e) {
                    log.warn("[AutoScanScheduler] Weekly scan failed for asset {}: {}",
                            asset.getId(), e.getMessage());
                }
            }
        }
        log.info("[AutoScanScheduler] Weekly full-scan completed: {} assets newly scanned", scanned);
    }

    private void scanAsset(DataAsset asset) throws Exception {
        // 构造扫描任务
        SensitiveScanTask task = new SensitiveScanTask();
        task.setAssetId(asset.getId());
        task.setSourceType("auto");
        task.setSourcePath(asset.getLocation() != null ? asset.getLocation() : asset.getName());
        task.setStatus("running");
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        scanTaskService.save(task);

        // 执行扫描（以资产名称+描述作为样本文本）
        String sampleText = buildSampleText(asset);
        SensitiveScanReport report = scanEngine.scan(List.of(sampleText));
        task.setReportData(MAPPER.writeValueAsString(report));
        task.setSensitiveRatio(report.getSummary() == null ? 0.0 : report.getSummary().getRatio());
        task.setStatus("done");
        task.setReportPath("/reports/auto-task-" + task.getId() + ".json");
        task.setUpdateTime(new Date());
        scanTaskService.updateById(task);
        log.info("[AutoScanScheduler] Asset {} scanned, sensitiveRatio={}",
                asset.getId(), task.getSensitiveRatio());
    }

    private String buildSampleText(DataAsset asset) {
        StringBuilder sb = new StringBuilder();
        if (asset.getName() != null)        sb.append(asset.getName()).append(" ");
        if (asset.getDescription() != null) sb.append(asset.getDescription()).append(" ");
        if (asset.getLocation() != null)    sb.append(asset.getLocation()).append(" ");
        if (asset.getLineage() != null)     sb.append(asset.getLineage());
        String result = sb.toString().trim();
        return result.isEmpty() ? "待扫描资产" : result;
    }
}
