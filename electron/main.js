/**
 * Aegis 轻量级客户端 – 主进程
 *
 * 功能：
 * 1. 以 Electron 框架加载 Aegis Workbench 前端（支持连接本地或远程服务端）
 * 2. 托盘图标：常驻后台，一键唤起主窗口
 * 3. 自动启动：写入系统开机自启
 * 4. 后台扫描：每隔 N 分钟执行一次影子AI扫描，将结果上报给服务端
 * 5. IPC 通道：渲染进程通过 preload.js 向主进程发指令（扫描、状态查询等）
 */

'use strict';

const { app, BrowserWindow, Tray, Menu, ipcMain, nativeImage, dialog, shell, safeStorage } = require('electron');
const path = require('path');
const fs   = require('fs');
const os   = require('os');
const cron = require('node-cron');
const { v4: uuidv4 } = require('uuid');
const scanner = require('./scanner/index');
const { createClipboardMonitor } = require('./scanner/clipboardMonitor');

process.on('uncaughtException', (err) => {
  console.error('[Aegis][main] uncaughtException:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('[Aegis][main] unhandledRejection:', reason);
});

// Some Windows devices render a black window with GPU acceleration enabled.
// Prefer stability for distributed installers.
if (process.platform === 'win32') {
  app.disableHardwareAcceleration();
  app.commandLine.appendSwitch('disable-gpu');
}

// ── 配置 ────────────────────────────────────────────────────────────────────

/** 服务端地址，优先读取环境变量或本地配置文件 */
const CONFIG_PATH = path.join(app.getPath('userData'), 'config.json');

const DEFAULT_SERVER_URL = app.isPackaged ? 'http://localhost:3000' : 'http://localhost:5173'; // 安装版默认连生产前端，开发版连 Vite
const DEFAULT_API_URL    = 'http://localhost:8080'; // Spring Boot 后端 API 地址（供扫描器上报）

function loadConfig() {
  const defaults = {
    serverUrl: DEFAULT_SERVER_URL,
    backendUrl: DEFAULT_API_URL,
    clientIngressToken: '',
    clientTokenEncrypted: '',
    companyId: 1,
    scanIntervalMinutes: 30,
    autoStart: true,
    minimizeToTray: true,
    enableClipboardMonitor: app.isPackaged ? false : true,
    requireLoginBeforeScan: true,
    requirePolicySync: true,
  };
  try {
    if (fs.existsSync(CONFIG_PATH)) {
      const saved = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf-8'));
      // ── 向后兼容迁移 ────────────────────────────────────────────────────
      // 将旧配置中保存在 serverUrl 里的后端地址（:8080）迁移到 backendUrl。
      // 同时修正任何看起来是后端 API 地址的 serverUrl（以 /api 开头，或端口 8080）。
      const migrateIfApiUrl = (url) => {
        if (!url) return url;
        try {
          const u = new URL(url);
          if (u.port === '8080' || u.pathname.startsWith('/api')) {
            return null; // signal to reset to default
          }
        } catch { /* invalid URL, ignore */ }
        return url;
      };

      if (saved.serverUrl) {
        const fixed = migrateIfApiUrl(saved.serverUrl);
        if (fixed === null) {
          // serverUrl was pointing at the backend API – move it to backendUrl and reset
          if (!saved.backendUrl) saved.backendUrl = saved.serverUrl;
          saved.serverUrl = defaults.serverUrl;
        }
      }
      // legacy apiUrl field → backendUrl
      if (saved.apiUrl && !saved.backendUrl) {
        saved.backendUrl = saved.apiUrl;
      }
      delete saved.apiUrl;

      // 打包版客户端应默认连接 3000 端口的前端容器。
      // 若用户沿用了开发模式的 5173，本地工作台会直接连接失败。
      const migratePackagedWorkbenchUrl = (url) => {
        if (!url || !app.isPackaged) return url;
        try {
          const u = new URL(url);
          const isLocalHost = u.hostname === 'localhost' || u.hostname === '127.0.0.1';
          if (isLocalHost && u.port === '5173') {
            return defaults.serverUrl;
          }
        } catch { /* ignore invalid URLs */ }
        return url;
      };

      if (saved.serverUrl) {
        saved.serverUrl = migratePackagedWorkbenchUrl(saved.serverUrl);
      }

      const merged = { ...defaults, ...saved };
      // Persist the corrected config so the fix survives next launch
      try { fs.writeFileSync(CONFIG_PATH, JSON.stringify(merged, null, 2)); } catch { /* ignore */ }
      return merged;
    }
  } catch { /* ignore */ }
  return defaults;
}

function buildOfflineHtml(workbenchUrl, reason) {
  const safeUrl = String(workbenchUrl || '').replace(/[<>&"']/g, (ch) => ({
    '<': '&lt;',
    '>': '&gt;',
    '&': '&amp;',
    '"': '&quot;',
    "'": '&#39;'
  }[ch] || ch));
  const safeReason = String(reason || '连接失败').replace(/[<>&"']/g, (ch) => ({
    '<': '&lt;',
    '>': '&gt;',
    '&': '&amp;',
    '"': '&quot;',
    "'": '&#39;'
  }[ch] || ch));

  return `<!doctype html>
<html>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Aegis 客户端</title>
    <style>
      :root { color-scheme: dark; }
      body {
        margin: 0;
        min-height: 100vh;
        display: grid;
        place-items: center;
        background: radial-gradient(circle at 20% 20%, #0f2542, #050710 60%);
        color: #b6c8df;
        font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
      }
      .panel {
        width: min(720px, calc(100vw - 48px));
        border: 1px solid rgba(102, 163, 255, 0.28);
        border-radius: 14px;
        background: rgba(8, 14, 28, 0.92);
        box-shadow: 0 16px 42px rgba(0, 0, 0, 0.45);
        padding: 24px;
      }
      h2 { margin: 0 0 10px; color: #64acff; }
      p { margin: 8px 0; line-height: 1.55; }
      code {
        display: block;
        margin-top: 12px;
        padding: 10px 12px;
        border-radius: 8px;
        background: rgba(15, 28, 52, 0.9);
        color: #d5e6ff;
        word-break: break-all;
      }
      .hint { color: #7f97b6; font-size: 12px; }
    </style>
  </head>
  <body>
    <section class="panel">
      <h2>Aegis 客户端连接失败</h2>
      <p>当前无法加载工作台页面，请检查服务端或前端容器状态。</p>
      <p>目标地址：</p>
      <code>${safeUrl}</code>
      <p>失败原因：${safeReason}</p>
      <p class="hint">可在托盘菜单「服务器设置」中修改工作台地址。</p>
    </section>
  </body>
</html>`;
}

function withClientLiteFlag(rawUrl) {
  const input = String(rawUrl || '').trim();
  if (!input) return input;
  if (!app.isPackaged) return input;
  try {
    const u = new URL(input);
    u.searchParams.set('clientLite', '1');
    return u.toString();
  } catch {
    const hasQuery = input.includes('?');
    const separator = hasQuery ? '&' : '?';
    return `${input}${separator}clientLite=1`;
  }
}

function encryptClientToken(token) {
  const text = String(token || '').trim();
  if (!text) return '';
  if (safeStorage && typeof safeStorage.isEncryptionAvailable === 'function' && safeStorage.isEncryptionAvailable()) {
    try {
      return safeStorage.encryptString(text).toString('base64');
    } catch {
      return text;
    }
  }
  return text;
}

function decryptClientToken(encoded) {
  const text = String(encoded || '').trim();
  if (!text) return '';
  if (safeStorage && typeof safeStorage.isEncryptionAvailable === 'function' && safeStorage.isEncryptionAvailable()) {
    try {
      return safeStorage.decryptString(Buffer.from(text, 'base64'));
    } catch {
      return '';
    }
  }
  return text;
}

function resolveWorkbenchUrl() {
  // AEGIS_DEV_URL is only for local development. Packaged installers must
  // always use persisted settings/defaults to avoid being forced back to 5173.
  const devOverride = !app.isPackaged ? process.env.AEGIS_DEV_URL : '';
  const normalized = normalizePackagedWorkbenchUrl(devOverride || config.serverUrl || DEFAULT_SERVER_URL);
  return withClientLiteFlag(normalized);
}

function normalizePackagedWorkbenchUrl(rawUrl) {
  const input = String(rawUrl || '').trim();
  if (!input || !app.isPackaged) return input;
  try {
    const u = new URL(input);
    const isLocalHost = u.hostname === 'localhost' || u.hostname === '127.0.0.1';
    if (!isLocalHost) return input;
    if (u.port === '5173' || u.port === '') {
      u.port = '3000';
      return u.toString();
    }
    return input;
  } catch {
    // If malformed but contains old local dev port, fallback to packaged default.
    if (input.includes('localhost:5173') || input.includes('127.0.0.1:5173')) {
      return DEFAULT_SERVER_URL;
    }
    return input;
  }
}

function saveConfig(cfg) {
  try {
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(cfg, null, 2));
  } catch { /* ignore */ }
}

/** 客户端唯一 ID，持久化在 userData 目录 */
const CLIENT_ID_PATH = path.join(app.getPath('userData'), 'client-id.txt');
const POLICY_SYNC_INTERVAL_MS = 60 * 1000;

function getOrCreateClientId() {
  try {
    if (fs.existsSync(CLIENT_ID_PATH)) {
      return fs.readFileSync(CLIENT_ID_PATH, 'utf-8').trim();
    }
  } catch { /* ignore */ }
  const id = uuidv4();
  try { fs.writeFileSync(CLIENT_ID_PATH, id); } catch { /* ignore */ }
  return id;
}

// ── 全局状态 ─────────────────────────────────────────────────────────────────

let mainWindow   = null;
let tray         = null;
let config       = loadConfig();
const CLIENT_ID  = getOrCreateClientId();
let lastScanResult = null;
let scanJob      = null;
let clipboardMonitor = null;
let policySyncJob = null;
let authState    = {
  authenticated: false,
  user: null,
  updatedAt: null,
};
let clientPolicyState = {
  synced: false,
  fetchedAt: 0,
  expiresAt: 0,
  policy: null,
  lastError: null,
};

function policyIsFresh() {
  return clientPolicyState.synced && clientPolicyState.expiresAt > Date.now();
}

function resolvePolicyUsername() {
  const authUsername = String(authState?.user?.username || '').trim();
  if (authUsername) {
    return authUsername;
  }
  return String(os.userInfo().username || '').trim();
}

function policyCapability(name, fallback = true) {
  const caps = clientPolicyState?.policy?.capabilities;
  if (!caps || typeof caps !== 'object') {
    return fallback;
  }
  if (!(name in caps)) {
    return fallback;
  }
  return Boolean(caps[name]);
}

function canRunScan() {
  if (config.requireLoginBeforeScan && !authState.authenticated) {
    return false;
  }
  if (!policyCapability('allowShadowScan', true)) {
    return false;
  }
  return true;
}

function resolveReportCompanyId() {
  const userCompanyId = Number(authState?.user?.companyId);
  if (Number.isFinite(userCompanyId) && userCompanyId > 0) {
    return userCompanyId;
  }
  const configuredCompanyId = Number(config.companyId);
  if (Number.isFinite(configuredCompanyId) && configuredCompanyId > 0) {
    return configuredCompanyId;
  }
  return 1;
}

function resolveClientIngressToken() {
  const clientToken = decryptClientToken(config.clientTokenEncrypted);
  if (clientToken) {
    return clientToken;
  }
  return String(config.clientIngressToken || process.env.AEGIS_CLIENT_TOKEN || '').trim();
}

function resolveBackendApiBase() {
  const raw = String(config.backendUrl || DEFAULT_API_URL || '').trim();
  if (!raw) return DEFAULT_API_URL;
  return raw.endsWith('/') ? raw.slice(0, -1) : raw;
}

async function syncClientPolicy(force = false) {
  const token = resolveClientIngressToken();
  if (!token) {
    clientPolicyState = {
      ...clientPolicyState,
      synced: false,
      lastError: 'missing-client-token',
    };
    return null;
  }
  const now = Date.now();
  if (!force && clientPolicyState.fetchedAt > 0 && now - clientPolicyState.fetchedAt < POLICY_SYNC_INTERVAL_MS) {
    return clientPolicyState.policy;
  }

  const endpoint = `${resolveBackendApiBase()}/api/client/policy/snapshot`;
  try {
    const resp = await fetch(endpoint, {
      method: 'GET',
      headers: {
        'X-Client-Token': token,
        'X-Company-Id': String(resolveReportCompanyId()),
        'X-Client-Id': CLIENT_ID,
        'X-Client-Username': resolvePolicyUsername(),
      },
    });
    const payload = await resp.json().catch(() => ({}));
    const data = payload?.data || payload || {};
    if (!resp.ok || Number(payload?.code || 20000) !== 20000) {
      throw new Error(payload?.msg || payload?.message || `HTTP ${resp.status}`);
    }

    const ttlSeconds = Math.max(30, Number(data.ttlSeconds || 180));
    clientPolicyState = {
      synced: true,
      fetchedAt: now,
      expiresAt: now + ttlSeconds * 1000,
      policy: data,
      lastError: null,
    };

    config.requireLoginBeforeScan = data.requireLoginBeforeScan !== false;
    config.requirePolicySync = data.policySyncRequired !== false;
    const allowClipboardMonitor = policyCapability('allowClipboardMonitor', true);
    if (allowClipboardMonitor !== Boolean(config.enableClipboardMonitor)) {
      config.enableClipboardMonitor = allowClipboardMonitor;
      saveConfig(config);
      if (clipboardMonitor && !allowClipboardMonitor) {
        clipboardMonitor.stop();
        clipboardMonitor = null;
      } else if (!clipboardMonitor && allowClipboardMonitor) {
        clipboardMonitor = createClipboardMonitor({
          getBackendUrl: () => config.backendUrl || config.serverUrl,
          getAuthState: () => authState,
          getClientToken: () => resolveClientIngressToken(),
          getCompanyId: () => resolveReportCompanyId(),
          getClientId: () => CLIENT_ID,
          allowLocalAlert: () => policyCapability('allowLocalAlertDialog', true),
        });
        clipboardMonitor.start().catch(() => {});
      }
    }

    updateTrayMenu();
    return data;
  } catch (err) {
    clientPolicyState = {
      ...clientPolicyState,
      synced: false,
      fetchedAt: now,
      expiresAt: 0,
      lastError: err?.message || 'policy-sync-failed',
    };
    return null;
  }
}

function startPolicySync() {
  if (policySyncJob) {
    clearInterval(policySyncJob);
    policySyncJob = null;
  }
  policySyncJob = setInterval(() => {
    syncClientPolicy(false).catch(() => {});
  }, POLICY_SYNC_INTERVAL_MS);
}

// ── 主窗口 ──────────────────────────────────────────────────────────────────

function createWindow() {
  mainWindow = new BrowserWindow({
    width:  1280,
    height: 800,
    minWidth:  900,
    minHeight: 600,
    title: 'Aegis 守护客户端',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    backgroundColor: '#050710',
    show: false, // 先隐藏，等内容加载好再显示
  });

  if (process.env.AEGIS_CLIENT_DEBUG === '1') {
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  }

  const ensureWindowVisible = () => {
    if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) {
      mainWindow.show();
    }
  };

  const safeReloadMainWindow = (delayMs = 0) => {
    if (!mainWindow || mainWindow.isDestroyed()) {
      return;
    }
    const run = () => {
      if (!mainWindow || mainWindow.isDestroyed()) return;
      const url = resolveWorkbenchUrl();
      mainWindow.loadURL(url).catch((err) => {
        loadOfflineFallback(err?.message || 'reload-failed');
      });
    };
    if (delayMs > 0) {
      setTimeout(run, delayMs);
      return;
    }
    run();
  };

  // 加载工作台（安装版默认连接 3000；失败时一定回退到可见的离线提示页）
  const workbenchUrl = resolveWorkbenchUrl();
  let mainLoadDone = false;
  let loadWatchdog = null;
  const loadOfflineFallback = (reason) => {
    const html = buildOfflineHtml(workbenchUrl, reason);
    const dataUrl = `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
    return mainWindow.loadURL(dataUrl).catch(() => {
      mainWindow.loadURL('about:blank').catch(() => {});
    }).finally(() => {
      ensureWindowVisible();
    });
  };

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL, isMainFrame) => {
    mainLoadDone = true;
    if (loadWatchdog) {
      clearTimeout(loadWatchdog);
      loadWatchdog = null;
    }
    if (!isMainFrame) return;
    if (!validatedURL || !validatedURL.startsWith('data:text/html')) {
      loadOfflineFallback(`${errorDescription || 'load-failed'} (${errorCode})`);
    }
  });

  // If subresources (JS/CSS chunks) fail repeatedly after main frame success,
  // surface the issue instead of leaving a blank/black shell.
  mainWindow.webContents.on('console-message', (_event, _level, message) => {
    if (!message) return;
    const text = String(message).toLowerCase();
    const looksLikeChunkError = (
      text.includes('failed to load resource')
      || text.includes('loading chunk')
      || text.includes('chunkloaderror')
      || text.includes('unexpected token <')
    );
    if (!looksLikeChunkError) return;
    if (!mainWindow || mainWindow.isDestroyed()) return;
    loadOfflineFallback(`renderer-resource-error: ${String(message).slice(0, 220)}`);
  });

  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    const reason = details?.reason || 'unknown';
    const exitCode = details?.exitCode ?? 0;
    loadOfflineFallback(`render-process-gone(${reason}, exit=${exitCode})`);
    safeReloadMainWindow(1200);
  });

  mainWindow.on('unresponsive', () => {
    loadOfflineFallback('window-unresponsive');
    safeReloadMainWindow(800);
  });

  mainWindow.webContents.on('did-finish-load', () => {
    mainLoadDone = true;
    if (loadWatchdog) {
      clearTimeout(loadWatchdog);
      loadWatchdog = null;
    }
    ensureWindowVisible();
  });

  mainWindow.loadURL(workbenchUrl).catch((err) => {
    mainLoadDone = true;
    if (loadWatchdog) {
      clearTimeout(loadWatchdog);
      loadWatchdog = null;
    }
    loadOfflineFallback(err?.message || 'load-url-failed');
  });

  // 兜底：网络挂起但未触发 fail-load 时，超时切到离线提示，避免长期卡黑屏/白屏。
  loadWatchdog = setTimeout(() => {
    if (!mainLoadDone) {
      loadOfflineFallback('load-timeout(12s)');
    }
  }, 12000);

  mainWindow.once('ready-to-show', () => {
    ensureWindowVisible();
  });

  // 兜底：极端情况下 ready-to-show 可能不触发，避免窗口长期不可见误判为黑屏。
  setTimeout(() => {
    ensureWindowVisible();
  }, 2500);

  mainWindow.on('close', (event) => {
    if (config.minimizeToTray && !app.isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
}

// ── 托盘 ─────────────────────────────────────────────────────────────────────

function createTray() {
  const iconPath = path.join(__dirname, 'assets', 'icon.png');
  let icon;
  if (fs.existsSync(iconPath)) {
    const raw = nativeImage.createFromPath(iconPath);
    // macOS tray icons should be 22×22; Windows/Linux 16×16 or 32×32.
    // Resize so the tray icon is never blank due to size issues.
    const size = process.platform === 'darwin' ? 22 : 32;
    icon = raw.isEmpty() ? nativeImage.createEmpty() : raw.resize({ width: size, height: size });
  } else {
    icon = nativeImage.createEmpty();
  }

  tray = new Tray(icon);
  tray.setToolTip('Aegis 守护客户端 – 正在保护您的数据');
  updateTrayMenu();

  tray.on('double-click', showWindow);
}

function updateTrayMenu() {
  const lastScan = lastScanResult
    ? `上次扫描：${new Date(lastScanResult.time).toLocaleTimeString('zh-CN')}`
    : '尚未扫描';

  const shadowCount = lastScanResult
    ? `发现影子AI：${lastScanResult.shadowAiCount} 个`
    : '';

  const contextMenu = Menu.buildFromTemplate([
    { label: 'Aegis 守护客户端', enabled: false },
    { label: `客户端 ID：${CLIENT_ID.slice(0, 8)}…`, enabled: false },
    { label: clientPolicyState.synced ? '策略状态：已同步' : '策略状态：待同步', enabled: false },
    { type: 'separator' },
    { label: lastScan, enabled: false },
    ...(shadowCount ? [{ label: shadowCount, enabled: false }] : []),
    { type: 'separator' },
    { label: '打开工作台', click: showWindow },
    {
      label: '立即扫描',
      click: () => {
        runScan()
          .then((result) => {
            if (result?.skipped) {
              dialog.showMessageBox({
                type: 'info',
                title: 'Aegis 提示',
                message: result.reason || '请先登录后再开始检测。',
              }).catch(() => {});
            }
          })
          .catch(console.error);
      },
    },
    {
      label: '开机自启',
      type: 'checkbox',
      checked: config.autoStart,
      click: (menuItem) => toggleAutoStart(menuItem.checked),
    },
    { type: 'separator' },
    {
      label: '服务器设置',
      click: () => showServerSettings(),
    },
    { type: 'separator' },
    {
      label: '退出',
      click: () => {
        app.isQuitting = true;
        app.quit();
      },
    },
  ]);
  tray.setContextMenu(contextMenu);
}

function showWindow() {
  if (!mainWindow) createWindow();
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

// ── 开机自启 ─────────────────────────────────────────────────────────────────

function toggleAutoStart(enable) {
  config.autoStart = enable;
  saveConfig(config);

  app.setLoginItemSettings({
    openAtLogin: enable,
    name: 'Aegis 守护客户端',
    args: ['--hidden'],
  });
}

function applyAutoStart() {
  if (config.autoStart) {
    app.setLoginItemSettings({
      openAtLogin: true,
      name: 'Aegis 守护客户端',
      args: ['--hidden'],
    });
  }
}

// ── 服务器设置 ────────────────────────────────────────────────────────────────

async function showServerSettings() {
  const win = new BrowserWindow({
    width: 520,
    height: 360,
    parent: mainWindow,
    modal: true,
    title: '服务器设置',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    backgroundColor: '#050710',
    resizable: false,
  });

  const inputStyle = 'width:100%;padding:10px;background:%230a1628;border:1px solid %23223355;border-radius:6px;color:%23cdd9e5;font-size:14px;box-sizing:border-box;margin-bottom:4px';
  const labelStyle = 'font-size:12px;color:%23889;margin:0 0 4px;display:block';
  const hintStyle  = 'font-size:11px;color:%23556;margin:0 0 14px';

  // Settings page uses the preload's IPC bridge to save the URL safely
  win.loadURL(`data:text/html;charset=utf-8,
    <html style="background:%23050710;color:%23cdd9e5;font-family:sans-serif;padding:24px;box-sizing:border-box">
      <h3 style="margin:0 0 16px;color:%2364acff">服务器设置</h3>
      <label style="${labelStyle}">前端工作台地址（Workbench URL）</label>
      <input id="serverUrl" value="${config.serverUrl}"
        style="${inputStyle}"/>
      <p style="${hintStyle}">开发模式通常为 http://localhost:5173；Docker/打包版工作台通常为 http://localhost:3000。</p>
      <label style="${labelStyle}">后端 API 地址（Backend API URL）</label>
      <input id="backendUrl" value="${config.backendUrl || 'http://localhost:8080'}"
        style="${inputStyle}"/>
      <p style="${hintStyle}">Spring Boot 后端地址，如 http://localhost:8080（客户端扫描上报使用）</p>
      <label style="${labelStyle}">公司 ID（Company ID）</label>
      <input id="companyId" value="${Number(config.companyId) || 1}"
        style="${inputStyle}"/>
      <p style="${hintStyle}">上报到多租户后端时用于归属公司，优先级低于已登录用户公司。</p>
      <label style="${labelStyle}">客户端上报令牌（Client Ingress Token）</label>
      <input id="clientIngressToken" value="${config.clientIngressToken || ''}"
        style="${inputStyle}"/>
      <p style="${hintStyle}">用于调用 /api/client/* 和 /api/privacy/events 等无会话上报接口。</p>
      <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:8px">
        <button onclick="window.close()"
          style="padding:8px 20px;background:transparent;border:1px solid %23334;color:%238ab;border-radius:6px;cursor:pointer">取消</button>
        <button id="saveBtn"
          style="padding:8px 20px;background:%231a3a7a;border:none;color:%23cde;border-radius:6px;cursor:pointer">保存</button>
      </div>
      <script>
        document.getElementById('saveBtn').onclick = function() {
          var serverUrl = document.getElementById('serverUrl').value.trim();
          var backendUrl = document.getElementById('backendUrl').value.trim();
          var companyId = Number(document.getElementById('companyId').value.trim() || '1');
          var clientIngressToken = document.getElementById('clientIngressToken').value.trim();
          if (window.aegisClient) {
            window.aegisClient.saveConfig({
              serverUrl: serverUrl,
              backendUrl: backendUrl,
              companyId: Number.isFinite(companyId) && companyId > 0 ? companyId : 1,
              clientIngressToken: clientIngressToken
            });
          }
          window.close();
        };
      </script>
    </html>
  `);
}

// ── 后台扫描 ─────────────────────────────────────────────────────────────────

async function runScan() {
  if (!canRunScan()) {
    const reason = '请先登录后再开始检测。';
    return {
      time: new Date().toISOString(),
      shadowAiCount: 0,
      riskLevel: 'none',
      services: [],
      skipped: true,
      reason,
    };
  }

  console.log('[Aegis] 开始影子AI扫描…');
  try {
    // Policy sync is best-effort: keep local scan available even when
    // remote policy endpoint is temporarily unreachable.
    const hasClientToken = Boolean(resolveClientIngressToken());
    if (config.requirePolicySync !== false && hasClientToken && !policyIsFresh()) {
      try {
        await syncClientPolicy(true);
      } catch (syncErr) {
        console.warn('[Aegis] 策略同步失败，使用本地默认策略继续扫描:', syncErr?.message || syncErr);
      }
    }

    const result = await scanner.scan({
      clientId: CLIENT_ID,
      backendUrl: config.backendUrl || config.serverUrl,
      companyId: resolveReportCompanyId(),
      clientToken: resolveClientIngressToken(),
      authenticatedUsername: authState?.authenticated ? authState?.user?.username : null,
    });
    lastScanResult = result;
    console.log(`[Aegis] 扫描完成，发现影子AI：${result.shadowAiCount} 个，风险等级：${result.riskLevel}`);

    // 通知渲染进程刷新
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('scan-complete', result);
    }

    updateTrayMenu();

    // 高风险时在托盘显示通知
    if (result.riskLevel === 'high' && tray) {
      const { Notification } = require('electron');
      if (Notification.isSupported()) {
        new Notification({
          title: 'Aegis 发现高风险影子AI',
          body: `在本机发现 ${result.shadowAiCount} 个未授权AI服务，请及时处理。`,
          icon: path.join(__dirname, 'assets', 'icon.png'),
        }).show();
      }
    }
    return result;
  } catch (err) {
    console.error('[Aegis] 扫描失败：', err.message);
    return {
      time: new Date().toISOString(),
      shadowAiCount: 0,
      riskLevel: 'none',
      services: [],
      failed: true,
      reason: err?.message || '扫描失败',
    };
  }
}

function startScheduledScan() {
  if (scanJob) {
    scanJob.stop();
    scanJob = null;
  }
  const interval = Math.max(1, config.scanIntervalMinutes);
  // cron: 每 N 分钟执行一次
  const cronExpr = `*/${interval} * * * *`;
  scanJob = cron.schedule(cronExpr, () => runScan().catch(console.error));
  console.log(`[Aegis] 定时扫描已启动，间隔：${interval} 分钟`);
}

// ── IPC 处理 ─────────────────────────────────────────────────────────────────

ipcMain.handle('get-client-info', () => ({
  clientId: CLIENT_ID,
  hostname:  os.hostname(),
  osUsername: os.userInfo().username,
  osType:    (() => {
    const p = process.platform;
    if (p === 'win32') return 'Windows';
    if (p === 'darwin') return 'macOS';
    return 'Linux';
  })(),
  serverUrl: config.serverUrl,
  backendUrl: config.backendUrl || 'http://localhost:8080',
  scanIntervalMinutes: config.scanIntervalMinutes,
  autoStart: config.autoStart,
  requireLoginBeforeScan: config.requireLoginBeforeScan,
  companyId: resolveReportCompanyId(),
  hasClientIngressToken: !!resolveClientIngressToken(),
  policyState: {
    synced: clientPolicyState.synced,
    fetchedAt: clientPolicyState.fetchedAt,
    expiresAt: clientPolicyState.expiresAt,
    lastError: clientPolicyState.lastError,
    roleCode: clientPolicyState.policy?.roleCode || '',
    configVersion: clientPolicyState.policy?.configVersion || 0,
  },
  authState,
  lastScanResult,
}));

ipcMain.handle('run-scan', async () => {
  const result = await runScan();
  return result;
});

ipcMain.handle('set-auth-state', async (event, state) => {
  authState = {
    authenticated: Boolean(state?.authenticated),
    user: state?.user || null,
    updatedAt: new Date().toISOString(),
  };
  if (canRunScan()) {
    setTimeout(() => runScan().catch(console.error), 500);
  }
  syncClientPolicy(true).catch(() => {});
  return authState;
});

ipcMain.handle('get-auth-state', () => authState);

ipcMain.on('save-server-url', (event, url) => {
  config.serverUrl = normalizePackagedWorkbenchUrl(url);
  saveConfig(config);
  if (mainWindow) {
    const nextUrl = withClientLiteFlag(config.serverUrl);
    mainWindow.loadURL(nextUrl).catch(console.error);
  }
  updateTrayMenu();
});

ipcMain.handle('get-config', () => config);
ipcMain.handle('save-config', (event, newConfig) => {
  const prevServerUrl = config.serverUrl;
  const nextConfig = { ...newConfig };
  if (Object.prototype.hasOwnProperty.call(nextConfig, 'serverUrl')) {
    nextConfig.serverUrl = normalizePackagedWorkbenchUrl(nextConfig.serverUrl);
  }
  config = { ...config, ...nextConfig };
  config.requireLoginBeforeScan = Boolean(config.requireLoginBeforeScan);
  saveConfig(config);
  startScheduledScan();
  // Reload the workbench if server URL changed
  if (config.serverUrl && config.serverUrl !== prevServerUrl && mainWindow) {
    const nextUrl = withClientLiteFlag(config.serverUrl);
    mainWindow.loadURL(nextUrl).catch((err) => {
      const html = buildOfflineHtml(nextUrl, err?.message || 'load-url-failed');
      const dataUrl = `data:text/html;charset=utf-8,${encodeURIComponent(html)}`;
      mainWindow.loadURL(dataUrl).catch(() => {});
    });
  }
  if (clipboardMonitor) {
    if (config.enableClipboardMonitor === false) {
      clipboardMonitor.stop();
      clipboardMonitor = null;
    } else {
      clipboardMonitor.refreshConfig(true).catch(() => {});
    }
  } else if (config.enableClipboardMonitor !== false) {
    clipboardMonitor = createClipboardMonitor({
      getBackendUrl: () => config.backendUrl || config.serverUrl,
      getAuthState: () => authState,
      getClientToken: () => resolveClientIngressToken(),
      getCompanyId: () => resolveReportCompanyId(),
      getClientId: () => CLIENT_ID,
      allowLocalAlert: () => policyCapability('allowLocalAlertDialog', true),
    });
    clipboardMonitor.start().catch(() => {});
  }
  syncClientPolicy(true).catch(() => {});
  updateTrayMenu();
  return config;
});

ipcMain.handle('save-client-token', (event, token) => {
  config.clientTokenEncrypted = encryptClientToken(token);
  saveConfig(config);
  updateTrayMenu();
  return { saved: Boolean(String(token || '').trim()) };
});

ipcMain.handle('clear-client-token', () => {
  config.clientTokenEncrypted = '';
  saveConfig(config);
  updateTrayMenu();
  return { cleared: true };
});

// ── 应用生命周期 ──────────────────────────────────────────────────────────────

app.whenReady().then(async () => {
  applyAutoStart();

  // 如果以 --hidden 启动（开机自启时），不显示主窗口
  const startHidden = process.argv.includes('--hidden');
  if (!startHidden) {
    createWindow();
  }

  createTray();
  startScheduledScan();
  startPolicySync();
  await syncClientPolicy(true);
  if (config.enableClipboardMonitor !== false) {
    clipboardMonitor = createClipboardMonitor({
      getBackendUrl: () => config.backendUrl || config.serverUrl,
      getAuthState: () => authState,
      getClientToken: () => resolveClientIngressToken(),
      getCompanyId: () => resolveReportCompanyId(),
      getClientId: () => CLIENT_ID,
      allowLocalAlert: () => policyCapability('allowLocalAlertDialog', true),
    });
    // Delay privacy monitor startup to keep app launch responsive on low-end machines.
    setTimeout(() => {
      if (clipboardMonitor) {
        clipboardMonitor.start().catch(() => {});
      }
    }, 8000);
  }
});

app.on('window-all-closed', () => {
  // On non-macOS platforms, quit the process when all windows are gone.
  // (On macOS, apps conventionally stay alive until the user explicitly quits.)
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  } else {
    showWindow();
  }
});

app.on('before-quit', () => {
  app.isQuitting = true;
  if (policySyncJob) {
    clearInterval(policySyncJob);
    policySyncJob = null;
  }
  if (clipboardMonitor) {
    clipboardMonitor.stop();
  }
});
