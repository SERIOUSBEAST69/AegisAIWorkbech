'use strict';

const crypto = require('crypto');
const os = require('os');
const axios = require('axios');
const { clipboard, Notification } = require('electron');
const { execFile } = require('child_process');
const { detectTypes, maskContent } = require('./privacyDetect');

const DEFAULT_CONFIG = {
  monitorEnabled: true,
  predictEnabled: true,
  predictEndpoint: 'http://localhost:5000/predict',
  dedupeSeconds: 60,
  configVersion: 1,
  syncIntervalSec: 60,
  aiWindowRules: {
    titleKeywords: ['ChatGPT', '豆包', '文心一言', 'Kimi', '通义千问'],
    processNames: ['chrome', 'msedge', 'firefox', 'doubao', 'qqbrowser'],
  },
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
    this.lastWarnByHash = new Map();
    this.timer = null;
    this.config = { ...DEFAULT_CONFIG };
    this.configVersion = Number(DEFAULT_CONFIG.configVersion || 1);
    this.lastConfigFetchAt = 0;
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
    const syncIntervalSec = Math.max(15, Number(this.config.syncIntervalSec || 60));
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
        params: { sinceVersion: this.configVersion || 1 },
      });
      const data = resp?.data;
      const payload = data?.data ? data.data : data;
      if (payload && typeof payload === 'object') {
        if (payload.changed === false) {
          this.configVersion = Number(payload.configVersion || this.configVersion || 1);
          this.config = {
            ...this.config,
            syncIntervalSec: Number(payload.syncIntervalSec || this.config.syncIntervalSec || 60),
          };
        } else {
          this.config = { ...DEFAULT_CONFIG, ...payload };
          this.configVersion = Number(this.config.configVersion || payload.configVersion || this.configVersion || 1);
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

    const current = String(clipboard.readText() || '').trim();
    if (!current || current === this.lastClipboard) {
      return;
    }
    this.lastClipboard = current;

    const localTypes = detectTypes(current);
    const predictTypes = await callPredict(this.config, current);
    const detectedTypes = Array.from(new Set([...localTypes, ...predictTypes]));
    if (detectedTypes.length === 0) {
      return;
    }

    const activeWindow = await this.getActiveWindow();
    const isAiWindow = this.matchAiWindow(activeWindow);
    const contentHash = crypto.createHash('sha256').update(current).digest('hex');
    const dedupeMs = Math.max(1, Number(this.config.dedupeSeconds || 60)) * 1000;
    const lastWarnAt = this.lastWarnByHash.get(contentHash) || 0;
    if (Date.now() - lastWarnAt < dedupeMs) {
      return;
    }

    const authState = this.getAuthState() || {};
    const username = authState?.user?.username || 'unknown';
    const payload = {
      userId: username,
      eventType: 'CLIPBOARD_SENSITIVE',
      content: current,
      source: 'clipboard',
      action: isAiWindow ? 'warn' : 'audit_only',
      timestamp: new Date().toISOString(),
      deviceId: `${username}-device`,
      hostname: os.hostname(),
      windowTitle: activeWindow?.title || '',
      matchedTypes: detectedTypes.join(','),
    };

    await this.reportEvent(payload);

    if (isAiWindow && Notification.isSupported()) {
      this.lastWarnByHash.set(contentHash, Date.now());
      new Notification({
        title: '隐私盾警告',
        body: '剪贴板中含有敏感信息，请勿粘贴到外部 AI 应用',
      }).show();
    }
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

  async reportEvent(payload) {
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
