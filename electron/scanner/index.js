/**
 * 影子AI扫描协调器
 *
 * 整合以下三种扫描方式：
 * 1. 浏览器历史扫描（识别最近访问的AI服务网站）
 * 2. 网络连接扫描（识别当前活跃的AI服务连接）
 * 3. 进程扫描（识别运行中的本地AI工具）
 *
 * 并将结果去重、评分后上报至 Aegis 服务端。
 */

'use strict';

const os   = require('os');
const path = require('path');
const axios = require('axios');

const { scanBrowserHistory }   = require('./browserHistoryScanner');
const { scanNetworkConnections } = require('./networkScanner');
const { scanRunningProcesses } = require('./processScanner');

// ── 辅助：合并去重 ──────────────────────────────────────────────────────────

/**
 * 合并多个来源的扫描结果，按服务名去重（保留最高风险来源）。
 * @param {object[][]} resultSets
 * @returns {object[]}
 */
function mergeResults(...resultSets) {
  const map = new Map();
  for (const results of resultSets) {
    for (const item of results) {
      const key = item.name;
      if (!map.has(key)) {
        map.set(key, item);
      } else {
        // 合并来源信息
        const existing = map.get(key);
        const sources = new Set(existing.source.split('|'));
        sources.add(item.source);
        existing.source = [...sources].join('|');
        // 取最新 lastSeen
        if (item.lastSeen && (!existing.lastSeen || item.lastSeen > existing.lastSeen)) {
          existing.lastSeen = item.lastSeen;
        }
        // 取更高风险级别
        const LEVELS = { high: 3, medium: 2, low: 1, none: 0 };
        if ((LEVELS[item.riskLevel] || 0) > (LEVELS[existing.riskLevel] || 0)) {
          existing.riskLevel = item.riskLevel;
        }
      }
    }
  }
  return [...map.values()];
}

// ── 辅助：计算综合风险等级 ──────────────────────────────────────────────────

function calcOverallRisk(services) {
  if (!services || services.length === 0) return 'none';
  if (services.some(s => s.riskLevel === 'high'))   return 'high';
  if (services.some(s => s.riskLevel === 'medium')) return 'medium';
  if (services.some(s => s.riskLevel === 'low'))    return 'low';
  return 'none';
}

// ── 主扫描函数 ────────────────────────────────────────────────────────────────

/**
 * 执行完整扫描并将结果上报至服务端。
 *
 * @param {{ clientId: string, backendUrl: string, serverUrl?: string, clientToken?: string, companyId?: number }} opts
 * @returns {Promise<{
 *   shadowAiCount: number,
 *   riskLevel: string,
 *   services: object[],
 *   time: string,
 * }>}
 */
async function scan({ clientId, backendUrl, serverUrl, clientToken, companyId }) {
  // backendUrl 优先；兼容旧调用方式中传入 serverUrl 的情况
  const apiBase = backendUrl || serverUrl;
  const startTime = Date.now();
  console.log('[Scanner] 开始扫描，clientId:', clientId);

  // 并行运行所有扫描器
  const [browserResults, networkResults, processResults] = await Promise.allSettled([
    Promise.resolve().then(() => {
      try { return scanBrowserHistory(); } catch (e) {
        console.warn('[Scanner] 浏览器历史扫描失败：', e.message);
        return [];
      }
    }),
    (async () => {
      try { return await scanNetworkConnections(); } catch (e) {
        console.warn('[Scanner] 网络连接扫描失败：', e.message);
        return [];
      }
    })(),
    Promise.resolve().then(() => {
      try { return scanRunningProcesses(); } catch (e) {
        console.warn('[Scanner] 进程扫描失败：', e.message);
        return [];
      }
    }),
  ]).then(results => results.map(r => (r.status === 'fulfilled' ? r.value : [])));

  const allServices = mergeResults(browserResults, networkResults, processResults);
  const riskLevel   = calcOverallRisk(allServices);
  const scanTime    = new Date().toISOString();

  console.log(`[Scanner] 扫描完成，耗时 ${Date.now() - startTime}ms，发现 ${allServices.length} 个影子AI服务`);

  // ── 上报至服务端 ────────────────────────────────────────────────────────────
  if (apiBase && clientId) {
    const report = {
      clientId,
      hostname:    os.hostname(),
      osUsername:  os.userInfo().username,
      osType:      getOsType(),
      clientVersion: '1.0.0',
      discoveredServices: JSON.stringify(allServices),
      shadowAiCount: allServices.length,
      riskLevel,
      scanTime,
    };

    try {
      const headers = { 'Content-Type': 'application/json' };
      if (clientToken) {
        headers['X-Client-Token'] = String(clientToken);
      }
      if (Number.isFinite(Number(companyId)) && Number(companyId) > 0) {
        headers['X-Company-Id'] = String(companyId);
      }
      await axios.post(`${apiBase}/api/client/report`, report, {
        timeout: 10000,
        headers,
      });
      console.log('[Scanner] 扫描报告已成功上报至服务端');
    } catch (err) {
      // 上报失败不影响本次扫描结果
      console.warn('[Scanner] 上报失败（将在下次重试）：', err.message);
    }
  }

  return {
    shadowAiCount: allServices.length,
    riskLevel,
    services: allServices,
    time: scanTime,
  };
}

// ── 辅助 ─────────────────────────────────────────────────────────────────────

function getOsType() {
  const p = process.platform;
  if (p === 'win32') return 'Windows';
  if (p === 'darwin') return 'macOS';
  return 'Linux';
}

module.exports = { scan };
