'use strict';

const crypto = require('crypto');
const os = require('os');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const { app, clipboard, Notification, dialog } = require('electron');
const { execFile } = require('child_process');
const { detectTypes, maskContent } = require('./privacyDetect');

const DEFAULT_EXFIL_WINDOW_RULES = {
  titleKeywords: ['微信', '企业微信', '钉钉', 'qq', 'wechat', 'dingtalk', 'wecom'],
  processNames: ['wechat', 'weixin', 'wxwork', 'dingtalk', 'qq', 'tim', 'chrome', 'msedge', 'firefox', 'qqbrowser'],
  processPathKeywords: ['wechat', 'dingtalk', 'qq', 'wxwork', 'browser'],
};

const FILE_HEADER_SCAN_BYTES = 256;
const FILE_SENSITIVE_KEYWORDS = [
  '机密',
  '绝密',
  '保密',
  '内部',
  '核心',
  '财务',
  '薪酬',
  '合同',
  '客户',
  '供应商',
  '报表',
  '方案',
  '密钥',
  '凭证',
  '台账',
  '涉密',
].map(item => item.toLowerCase());

const DEFAULT_CONFIG = {
  monitorEnabled: true,
  predictEnabled: true,
  predictEndpoint: 'http://localhost:5000/predict',
  dedupeSeconds: 60,
  exfilObservationSec: 45,
  configHotReloadSec: 5,
  configVersion: 1,
  configChecksum: '',
  syncIntervalSec: 60,
  sensitiveKeywords: ['身份证', '银行卡', '手机号', '公司代码'],
  sensitiveFileRules: {
    extensions: ['doc', 'docx', 'xls', 'xlsx', 'csv', 'pdf', 'ppt', 'pptx', 'txt', 'rtf'],
    pathKeywords: ['confidential', 'secret', '核心', '机密', '财务', '合同', '投标', '预算'],
  },
  aiWindowRules: {
    titleKeywords: ['通义', '文心', 'DeepSeek', '稿定', 'ModelWhale', '即梦', '豆包', '星火', 'Kimi', '混元', '智谱'],
    processNames: ['chrome', 'msedge', 'firefox', 'doubao', 'qqbrowser'],
  },
  exfilWindowRules: DEFAULT_EXFIL_WINDOW_RULES,
};

function queryActiveWindowByPowerShell() {
  const script = [
    "$sig = @'",
    'using System;',
    'using System.Text;',
    'using System.Runtime.InteropServices;',
    'public class Win32Api {',
    '  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();',
    '  [DllImport("user32.dll", SetLastError=true)] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);',
    '  [DllImport("user32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);',
    '}',
    "'@",
    'Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null',
    '$h = [Win32Api]::GetForegroundWindow()',
    '$pid = 0',
    '[Win32Api]::GetWindowThreadProcessId($h, [ref]$pid) | Out-Null',
    '$sb = New-Object System.Text.StringBuilder 1024',
    '[Win32Api]::GetWindowText($h, $sb, $sb.Capacity) | Out-Null',
    '$p = Get-Process -Id $pid -ErrorAction SilentlyContinue',
    '$obj = [pscustomobject]@{',
    '  title = $sb.ToString();',
    '  processName = if ($p) { $p.ProcessName } else { "" };',
    '  processPath = if ($p) { $p.Path } else { "" }',
    '}',
    '$obj | ConvertTo-Json -Compress',
  ].join('; ');

  return new Promise((resolve) => {
    execFile('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script], {
      windowsHide: true,
      timeout: 2500,
      maxBuffer: 1024 * 64,
    }, (error, stdout) => {
      if (error || !stdout) {
        resolve(null);
        return;
      }
      try {
        const parsed = JSON.parse(String(stdout).trim());
        resolve({
          title: parsed.title || '',
          owner: {
            name: parsed.processName || '',
            path: parsed.processPath || '',
          },
        });
      } catch {
        resolve(null);
      }
    });
  });
}

function normalizeText(value) {
  return String(value || '').toLowerCase();
}

function normalizeArray(input, fallback = []) {
  if (Array.isArray(input)) {
    return input.map(item => String(item || '').trim()).filter(Boolean);
  }
  if (typeof input === 'string') {
    return input
      .split(/[;,，\n]/)
      .map(item => item.trim())
      .filter(Boolean);
  }
  return [...fallback];
}

function normalizeKeywords(config) {
  const fromConfig = normalizeArray(config?.sensitiveKeywords, DEFAULT_CONFIG.sensitiveKeywords);
  return [...new Set(fromConfig.map(item => item.toLowerCase()))];
}

function detectKeywordTypes(text, keywords) {
  const lowered = normalizeText(text);
  if (!lowered || !keywords || keywords.length === 0) {
    return [];
  }
  const hits = [];
  for (const keyword of keywords) {
    if (keyword && lowered.includes(keyword)) {
      hits.push(`keyword:${keyword}`);
    }
  }
  return hits;
}

function buildAuditFilePath() {
  try {
    return path.join(app.getPath('userData'), 'clipboard-security-audit.log');
  } catch {
    return path.join(process.cwd(), 'clipboard-security-audit.log');
  }
}

function queryClipboardFileListByPowerShell() {
  const script = [
    '$files = Get-Clipboard -Format FileDropList -ErrorAction SilentlyContinue',
    'if ($null -eq $files) { @() | ConvertTo-Json -Compress }',
    'else { $files | ConvertTo-Json -Compress }',
  ].join('; ');

  return new Promise((resolve) => {
    execFile('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script], {
      windowsHide: true,
      timeout: 2200,
      maxBuffer: 1024 * 64,
    }, (error, stdout) => {
      if (error || !stdout) {
        resolve([]);
        return;
      }
      try {
        const parsed = JSON.parse(String(stdout).trim());
        if (Array.isArray(parsed)) {
          resolve(parsed.map(item => String(item || '').trim()).filter(Boolean));
          return;
        }
        if (typeof parsed === 'string' && parsed.trim()) {
          resolve([parsed.trim()]);
          return;
        }
      } catch {
        // ignore parse failure
      }
      resolve([]);
    });
  });
}

function extensionOf(filePath) {
  const base = String(filePath || '').split(/[\\/]/).pop() || '';
  const idx = base.lastIndexOf('.');
  return idx >= 0 ? base.slice(idx + 1).toLowerCase() : '';
}

function hasSensitiveKeyword(text, keywords = FILE_SENSITIVE_KEYWORDS) {
  const lowered = normalizeText(text);
  if (!lowered) {
    return false;
  }
  return keywords.some(keyword => keyword && lowered.includes(keyword));
}

async function readFileHeaderText(filePath, byteLimit = FILE_HEADER_SCAN_BYTES) {
  let fd;
  try {
    fd = await fs.promises.open(filePath, 'r');
    const size = Math.max(1, Number(byteLimit || FILE_HEADER_SCAN_BYTES));
    const buffer = Buffer.alloc(size);
    const { bytesRead } = await fd.read(buffer, 0, size, 0);
    if (!bytesRead) {
      return '';
    }
    return buffer.toString('utf8', 0, bytesRead);
  } catch {
    return '';
  } finally {
    if (fd) {
      try {
        await fd.close();
      } catch {
        // ignore close failure to keep monitor loop resilient
      }
    }
  }
}

async function callPredict(config, text) {
  if (!config?.predictEnabled || !config?.predictEndpoint || !text) {
    return [];
  }
  try {
    const resp = await axios.post(config.predictEndpoint, { text }, { timeout: 3000 });
    const payload = resp?.data || {};
    const label = String(payload.label || '').toLowerCase();
    if (label && label !== 'unknown') {
      return [label];
    }
  } catch {
    // Predict is best-effort; regex still works offline.
  }
  return [];
}

class ClipboardPrivacyMonitor {
  constructor({ getBackendUrl, getAuthState, getClientToken, getCompanyId }) {
    this.getBackendUrl = getBackendUrl;
    this.getAuthState = getAuthState;
    this.getClientToken = getClientToken;
    this.getCompanyId = getCompanyId;
    this.lastClipboard = '';
    this.lastClipboardFileSignature = '';
    this.lastWarnByHash = new Map();
    this.pendingSensitiveCopies = [];
    this.lastRiskAlertByKey = new Map();
    this.timer = null;
    this.config = { ...DEFAULT_CONFIG };
    this.configVersion = Number(DEFAULT_CONFIG.configVersion || 1);
    this.configChecksum = String(DEFAULT_CONFIG.configChecksum || '');
    this.lastConfigFetchAt = 0;
    this.auditFilePath = buildAuditFilePath();
  }

  async start() {
    if (this.timer) return;
    await this.refreshConfig(true);
    this.timer = setInterval(() => {
      this.tick().catch(() => {});
    }, 900);
  }

  stop() {
    if (!this.timer) return;
    clearInterval(this.timer);
    this.timer = null;
  }

  async refreshConfig(force = false) {
    const now = Date.now();
    const configuredSync = Number(this.config.syncIntervalSec || 60);
    const hotReload = Math.max(3, Number(this.config.configHotReloadSec || DEFAULT_CONFIG.configHotReloadSec));
    const syncIntervalSec = Math.max(3, Math.min(configuredSync, hotReload));
    if (!force && now - this.lastConfigFetchAt < syncIntervalSec * 1000) {
      return;
    }
    this.lastConfigFetchAt = now;

    const backendUrl = this.getBackendUrl();
    if (!backendUrl) {
      return;
    }
    try {
      const resp = await axios.get(`${backendUrl}/api/privacy/config/public`, {
        timeout: 3000,
        params: {
          sinceVersion: this.configVersion || 1,
          sinceChecksum: this.configChecksum || '',
        },
      });
      const data = resp?.data;
      const payload = data?.data ? data.data : data;
      if (payload && typeof payload === 'object') {
        if (payload.changed === false) {
          this.configVersion = Number(payload.configVersion || this.configVersion || 1);
          this.configChecksum = String(payload.configChecksum || this.configChecksum || '');
          this.config = {
            ...this.config,
            syncIntervalSec: Number(payload.syncIntervalSec || this.config.syncIntervalSec || 60),
          };
        } else {
          this.config = { ...DEFAULT_CONFIG, ...payload };
          this.configVersion = Number(this.config.configVersion || payload.configVersion || this.configVersion || 1);
          this.configChecksum = String(this.config.configChecksum || payload.configChecksum || this.configChecksum || '');
        }
      }
    } catch {
      // Keep defaults.
    }
  }

  async tick() {
    await this.refreshConfig(false);
    if (!this.config.monitorEnabled) {
      return;
    }

    const activeWindow = await this.getActiveWindow();
    await this.checkPendingExfilRisk(activeWindow);

    const current = String(clipboard.readText() || '').trim();
    const fileList = await queryClipboardFileListByPowerShell();
    const fileSignature = fileList.join('|').toLowerCase();

    const textChanged = current && current !== this.lastClipboard;
    const filesChanged = fileList.length > 0 && fileSignature !== this.lastClipboardFileSignature;
    if (!textChanged && !filesChanged) {
      return;
    }

    if (filesChanged) {
      await this.handleSensitiveFileCopy(fileList, activeWindow);
      this.lastClipboardFileSignature = fileSignature;
    }

    if (textChanged) {
      await this.handleSensitiveTextCopy(current, activeWindow);
      this.lastClipboard = current;
    }
  }

  async handleSensitiveTextCopy(current, activeWindow) {

    const localTypes = detectTypes(current);
    const predictTypes = await callPredict(this.config, current);
    const keywordTypes = detectKeywordTypes(current, normalizeKeywords(this.config));
    const detectedTypes = Array.from(new Set([...localTypes, ...predictTypes, ...keywordTypes]));
    if (detectedTypes.length === 0) {
      return;
    }

    const isAiWindow = this.matchAiWindow(activeWindow);
    const exfilTarget = this.matchExfilWindow(activeWindow);
    const contentHash = crypto.createHash('sha256').update(current).digest('hex');
    if (this.isDedupeHit(contentHash)) {
      return;
    }

    const eventContext = this.buildEventContext(activeWindow);
    if (!eventContext.username) {
      return;
    }
    const severity = this.resolveSeverity(detectedTypes, exfilTarget != null);
    const copyRecord = {
      content: current,
      contentHash,
      detectedTypes,
      username: eventContext.username,
      copyAt: Date.now(),
      copyAtIso: eventContext.nowIso,
      copyWindowTitle: eventContext.copyWindowTitle,
      copyProcessName: eventContext.copyProcessName,
      severity,
    };
    this.pushPendingCopy(copyRecord);

    const payload = {
      userId: eventContext.username,
      eventType: exfilTarget ? 'CLIPBOARD_TEXT_EXFIL' : 'CLIPBOARD_SENSITIVE',
      content: current,
      source: 'clipboard',
      action: exfilTarget ? 'block' : (isAiWindow ? 'warn' : 'audit_only'),
      severity,
      timestamp: eventContext.nowIso,
      deviceId: `${eventContext.username}-device`,
      hostname: os.hostname(),
      windowTitle: this.composeWindowTitle(eventContext.copyWindowTitle, eventContext.copyProcessName, exfilTarget),
      matchedTypes: detectedTypes.join(','),
    };

    this.writeLocalAudit({
      ...payload,
      copyWindowTitle: eventContext.copyWindowTitle,
      copyProcessName: eventContext.copyProcessName,
      contentDigest: contentHash,
      contentPreview: maskContent(current).slice(0, 180),
    });
    await this.reportEvent(payload);

    if (isAiWindow && Notification.isSupported()) {
      this.markWarned(contentHash);
      new Notification({
        title: '隐私盾警告',
        body: '剪贴板中含有敏感信息，请勿粘贴到外部 AI 应用',
      }).show();
    }

    this.markWarned(contentHash);
    this.showForcedRiskDialog({
      kind: exfilTarget ? 'text-exfil' : 'text-sensitive',
      exfilTarget,
      detectedTypes,
      rawContent: current,
    });
  }

  async handleSensitiveFileCopy(fileList, activeWindow) {
    const sensitiveFiles = await this.matchSensitiveFiles(fileList);
    if (sensitiveFiles.length === 0) {
      return;
    }

    const exfilTarget = this.matchExfilWindow(activeWindow);
    const rawContent = sensitiveFiles.join('; ');
    const contentHash = crypto.createHash('sha256').update(rawContent).digest('hex');
    if (this.isDedupeHit(contentHash)) {
      return;
    }

    const eventContext = this.buildEventContext(activeWindow);
    if (!eventContext.username) {
      return;
    }
    const detectedTypes = sensitiveFiles.map(file => `file:${extensionOf(file) || 'unknown'}`);
    const severity = exfilTarget ? 'critical' : 'high';

    const copyRecord = {
      kind: 'file',
      content: rawContent,
      contentHash,
      detectedTypes,
      username: eventContext.username,
      copyAt: Date.now(),
      copyAtIso: eventContext.nowIso,
      copyWindowTitle: eventContext.copyWindowTitle,
      copyProcessName: eventContext.copyProcessName,
      severity,
    };
    this.pushPendingCopy(copyRecord);

    const payload = {
      userId: eventContext.username,
      eventType: exfilTarget ? 'CLIPBOARD_FILE_EXFIL' : 'CLIPBOARD_FILE_SENSITIVE',
      content: rawContent,
      source: 'clipboard',
      action: exfilTarget ? 'block' : 'warn',
      severity,
      timestamp: eventContext.nowIso,
      deviceId: `${eventContext.username}-device`,
      hostname: os.hostname(),
      windowTitle: this.composeWindowTitle(eventContext.copyWindowTitle, eventContext.copyProcessName, exfilTarget),
      matchedTypes: [
        ...detectedTypes,
        ...sensitiveFiles.map(file => `file_path:${String(file).slice(0, 80)}`),
        ...(exfilTarget ? ['exfil_file_critical', `exfil:${normalizeText(exfilTarget.hit)}`] : []),
      ].join(','),
    };

    this.writeLocalAudit({
      ...payload,
      copyWindowTitle: eventContext.copyWindowTitle,
      copyProcessName: eventContext.copyProcessName,
      contentDigest: contentHash,
      contentPreview: sensitiveFiles.join('; ').slice(0, 220),
      copiedFiles: sensitiveFiles,
    });
    await this.reportEvent(payload);
    this.markWarned(contentHash);

    this.showForcedRiskDialog({
      kind: exfilTarget ? 'file-exfil' : 'file-sensitive',
      exfilTarget,
      detectedTypes,
      rawContent,
    });
  }

  async getActiveWindow() {
    return queryActiveWindowByPowerShell();
  }

  matchAiWindow(activeWindow) {
    if (!activeWindow) return false;

    const title = normalizeText(activeWindow.title);
    const processName = normalizeText(activeWindow.owner?.name);
    const processPath = normalizeText(activeWindow.owner?.path);
    const rules = this.config?.aiWindowRules || {};
    const titleKeywords = Array.isArray(rules.titleKeywords) ? rules.titleKeywords : [];
    const processNames = Array.isArray(rules.processNames) ? rules.processNames : [];

    const titleMatched = titleKeywords.some((kw) => title.includes(normalizeText(kw)));
    const processMatched = processNames.some((name) => {
      const n = normalizeText(name);
      return processName.includes(n) || processPath.includes(n);
    });
    return titleMatched || processMatched;
  }

  matchExfilWindow(activeWindow) {
    if (!activeWindow) return null;

    const title = normalizeText(activeWindow.title);
    const processName = normalizeText(activeWindow.owner?.name);
    const processPath = normalizeText(activeWindow.owner?.path);
    const rules = this.config?.exfilWindowRules || DEFAULT_EXFIL_WINDOW_RULES;
    const titleKeywords = normalizeArray(rules.titleKeywords, DEFAULT_EXFIL_WINDOW_RULES.titleKeywords).map(normalizeText);
    const processNames = normalizeArray(rules.processNames, DEFAULT_EXFIL_WINDOW_RULES.processNames).map(normalizeText);
    const processPathKeywords = normalizeArray(rules.processPathKeywords, DEFAULT_EXFIL_WINDOW_RULES.processPathKeywords).map(normalizeText);

    const titleHit = titleKeywords.find(kw => kw && title.includes(kw));
    const processHit = processNames.find(name => name && processName.includes(name));
    const pathHit = processPathKeywords.find(kw => kw && processPath.includes(kw));
    if (!titleHit && !processHit && !pathHit) {
      return null;
    }
    return {
      title: activeWindow.title || '',
      processName: activeWindow.owner?.name || '',
      processPath: activeWindow.owner?.path || '',
      hit: titleHit || processHit || pathHit || 'external-app',
    };
  }

  pushPendingCopy(copyRecord) {
    const windowMs = Math.max(10, Number(this.config.exfilObservationSec || 45)) * 1000;
    const expireAt = copyRecord.copyAt + windowMs;
    this.pendingSensitiveCopies.push({ ...copyRecord, expireAt });
    this.pendingSensitiveCopies = this.pendingSensitiveCopies.filter(item => item.expireAt > Date.now());
  }

  buildEventContext(activeWindow) {
    const authState = this.getAuthState() || {};
    const username = authState?.authenticated ? authState?.user?.username : null;
    return {
      username: username && String(username).trim() ? String(username).trim() : null,
      nowIso: new Date().toISOString(),
      copyWindowTitle: activeWindow?.title || '',
      copyProcessName: activeWindow?.owner?.name || '',
    };
  }

  isDedupeHit(contentHash) {
    const dedupeMs = Math.max(1, Number(this.config.dedupeSeconds || 60)) * 1000;
    const lastWarnAt = this.lastWarnByHash.get(contentHash) || 0;
    return Date.now() - lastWarnAt < dedupeMs;
  }

  markWarned(contentHash) {
    this.lastWarnByHash.set(contentHash, Date.now());
  }

  async checkPendingExfilRisk(activeWindow) {
    const exfilTarget = this.matchExfilWindow(activeWindow);
    if (!exfilTarget) {
      return;
    }
    const now = Date.now();
    this.pendingSensitiveCopies = this.pendingSensitiveCopies.filter(item => item.expireAt > now);
    if (this.pendingSensitiveCopies.length === 0) {
      return;
    }

    const latest = this.pendingSensitiveCopies[this.pendingSensitiveCopies.length - 1];
    if (!latest?.username) {
      return;
    }
    const riskKey = `${latest.contentHash}:${normalizeText(exfilTarget.processName)}:${Math.floor(now / 1000)}`;
    const recent = this.lastRiskAlertByKey.get(riskKey);
    if (recent && now - recent < 1500) {
      return;
    }
    this.lastRiskAlertByKey.set(riskKey, now);

    const payload = {
      userId: latest.username,
      eventType: latest.kind === 'file' ? 'CLIPBOARD_FILE_EXFIL' : 'CLIPBOARD_TEXT_EXFIL',
      content: latest.content,
      source: 'clipboard',
      action: 'block',
      severity: latest.kind === 'file' ? 'critical' : 'high',
      timestamp: new Date().toISOString(),
      deviceId: `${latest.username}-device`,
      hostname: os.hostname(),
      windowTitle: this.composeWindowTitle(latest.copyWindowTitle, latest.copyProcessName, exfilTarget),
      matchedTypes: [
        ...latest.detectedTypes,
        `exfil:${normalizeText(exfilTarget.hit)}`,
        latest.kind === 'file' ? 'exfil_file_critical' : 'exfil_risk',
      ].join(','),
    };

    this.writeLocalAudit({
      ...payload,
      contentDigest: latest.contentHash,
      contentPreview: maskContent(latest.content).slice(0, 180),
      copyTime: latest.copyAtIso,
      exfilWindowTitle: exfilTarget.title,
      exfilProcessName: exfilTarget.processName,
    });
    await this.reportEvent(payload);
    this.showForcedRiskDialog({
      kind: latest.kind === 'file' ? 'file-exfil' : 'text-exfil',
      exfilTarget,
      detectedTypes: latest.detectedTypes,
      rawContent: latest.content,
    });
  }

  resolveSeverity(detectedTypes, hasExfil) {
    if (hasExfil) {
      return 'high';
    }
    const lowered = (detectedTypes || []).map(item => normalizeText(item));
    if (lowered.some(item => item.includes('id_card') || item.includes('bank_card') || item.startsWith('keyword:'))) {
      return 'high';
    }
    if (lowered.some(item => item.includes('phone') || item.includes('company_code'))) {
      return 'medium';
    }
    return lowered.length > 0 ? 'low' : 'none';
  }

  composeWindowTitle(copyWindowTitle, copyProcessName, exfilTarget) {
    if (!exfilTarget) {
      return copyWindowTitle || '';
    }
    const srcTitle = copyWindowTitle || 'unknown-window';
    const srcProcess = copyProcessName || 'unknown-process';
    const targetTitle = exfilTarget.title || 'unknown-window';
    const targetProcess = exfilTarget.processName || 'unknown-process';
    return `copy:${srcTitle} (${srcProcess}) -> exfil:${targetTitle} (${targetProcess})`;
  }

  async matchSensitiveFiles(fileList) {
    const sensitive = [];
    for (const file of fileList) {
      const fileName = String(file || '').split(/[\\/]/).pop() || '';
      if (hasSensitiveKeyword(fileName)) {
        sensitive.push(file);
        continue;
      }
      const headerText = await readFileHeaderText(file, FILE_HEADER_SCAN_BYTES);
      if (hasSensitiveKeyword(headerText)) {
        sensitive.push(file);
      }
    }
    return sensitive;
  }

  showForcedRiskDialog({ kind, exfilTarget, detectedTypes, rawContent }) {
    const labels = (detectedTypes || []).slice(0, 6).join(', ') || '敏感字段';
    const preview = maskContent(rawContent || '').slice(0, 80);
    const processName = exfilTarget?.processName || 'unknown';
    const title = exfilTarget?.title || '';
    const isFile = kind === 'file-exfil' || kind === 'file-sensitive';
    const isExfil = kind === 'file-exfil' || kind === 'text-exfil';
    const header = isFile ? '敏感文件复制拦截' : '敏感文本复制拦截';
    const msg = isExfil ? '检测到复制后外传行为，已按高风险阻断并上报。' : '检测到敏感复制行为，已进入强监控并上报。';
    dialog.showMessageBox({
      type: 'warning',
      buttons: ['已知悉'],
      defaultId: 0,
      noLink: true,
      title: header,
      message: msg,
      detail: `目标进程: ${processName}\n目标窗口: ${title}\n命中类型: ${labels}\n内容预览: ${preview}`,
    }).catch(() => {});
  }

  writeLocalAudit(record) {
    try {
      const line = JSON.stringify({
        ...record,
        auditedAt: new Date().toISOString(),
      });
      fs.appendFileSync(this.auditFilePath, `${line}\n`, 'utf8');
    } catch {
      // Keep monitor loop resilient.
    }
  }

  async reportEvent(payload) {
    if (!payload?.userId || !String(payload.userId).trim()) {
      return;
    }
    const backendUrl = this.getBackendUrl();
    if (!backendUrl) {
      return;
    }
    try {
      const headers = { 'Content-Type': 'application/json' };
      const clientToken = typeof this.getClientToken === 'function' ? this.getClientToken() : '';
      const companyId = typeof this.getCompanyId === 'function' ? this.getCompanyId() : null;
      if (clientToken) {
        headers['X-Client-Token'] = String(clientToken);
      }
      if (Number.isFinite(Number(companyId)) && Number(companyId) > 0) {
        headers['X-Company-Id'] = String(companyId);
      }
      await axios.post(`${backendUrl}/api/privacy/events`, {
        ...payload,
        content: maskContent(payload.content),
      }, {
        timeout: 4000,
        headers,
      });
    } catch {
      // Ignore to keep tray process resilient.
    }
  }
}

function createClipboardMonitor(options) {
  return new ClipboardPrivacyMonitor(options);
}

module.exports = {
  createClipboardMonitor,
};
