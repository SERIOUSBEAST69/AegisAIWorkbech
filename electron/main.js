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

const { app, BrowserWindow, Tray, Menu, ipcMain, nativeImage, dialog, shell } = require('electron');
const path = require('path');
const fs   = require('fs');
const os   = require('os');
const cron = require('node-cron');
const { v4: uuidv4 } = require('uuid');
const scanner = require('./scanner/index');
const { createClipboardMonitor } = require('./scanner/clipboardMonitor');

// ── 配置 ────────────────────────────────────────────────────────────────────

/** 服务端地址，优先读取环境变量或本地配置文件 */
const CONFIG_PATH = path.join(app.getPath('userData'), 'config.json');

const DEFAULT_SERVER_URL = 'http://localhost:5173'; // Vue 前端地址（供 BrowserWindow 加载）
const DEFAULT_API_URL    = 'http://localhost:8080'; // Spring Boot 后端 API 地址（供扫描器上报）

function loadConfig() {
  const defaults = {
    serverUrl: 'http://localhost:5173',
    backendUrl: 'http://localhost:8080',
    clientIngressToken: '',
    companyId: 1,
    scanIntervalMinutes: 30,
    autoStart: true,
    minimizeToTray: true,
    requireLoginBeforeScan: true,
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

      const merged = { ...defaults, ...saved };
      // Persist the corrected config so the fix survives next launch
      try { fs.writeFileSync(CONFIG_PATH, JSON.stringify(merged, null, 2)); } catch { /* ignore */ }
      return merged;
    }
  } catch { /* ignore */ }
  return defaults;
}

function saveConfig(cfg) {
  try {
    fs.writeFileSync(CONFIG_PATH, JSON.stringify(cfg, null, 2));
  } catch { /* ignore */ }
}

/** 客户端唯一 ID，持久化在 userData 目录 */
const CLIENT_ID_PATH = path.join(app.getPath('userData'), 'client-id.txt');

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
let authState    = {
  authenticated: false,
  user: null,
  updatedAt: null,
};

function canRunScan() {
  return !config.requireLoginBeforeScan || authState.authenticated;
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
  return String(config.clientIngressToken || process.env.AEGIS_CLIENT_TOKEN || '').trim();
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

  // 加载工作台
  const workbenchUrl = process.env.AEGIS_DEV_URL || config.serverUrl;
  mainWindow.loadURL(workbenchUrl).catch(() => {
    // 如果无法连接服务端，展示一个简单的离线提示页
    mainWindow.loadURL(`data:text/html;charset=utf-8,
      <html style="background:#050710;color:#8ab4d4;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0">
        <div style="text-align:center">
          <h2 style="color:#64acff">Aegis 客户端</h2>
          <p>无法连接到服务端：${workbenchUrl}</p>
          <p style="font-size:12px;color:#555">请检查服务端是否运行，或在托盘菜单中修改服务器地址。</p>
        </div>
      </html>
    `);
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

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
      <p style="${hintStyle}">Vue 前端地址，如 http://localhost:5173（开发模式）</p>
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
    return {
      time: new Date().toISOString(),
      shadowAiCount: 0,
      riskLevel: 'none',
      services: [],
      skipped: true,
      reason: '请先登录后再开始检测。',
    };
  }

  console.log('[Aegis] 开始影子AI扫描…');
  try {
    const result = await scanner.scan({
      clientId: CLIENT_ID,
      backendUrl: config.backendUrl || config.serverUrl,
      companyId: resolveReportCompanyId(),
      clientToken: resolveClientIngressToken(),
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
  } catch (err) {
    console.error('[Aegis] 扫描失败：', err.message);
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
  authState,
  lastScanResult,
}));

ipcMain.handle('run-scan', async () => {
  const result = await runScan();
  if (result && !result.skipped) {
    return lastScanResult;
  }
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
  return authState;
});

ipcMain.handle('get-auth-state', () => authState);

ipcMain.on('save-server-url', (event, url) => {
  config.serverUrl = url;
  saveConfig(config);
  if (mainWindow) mainWindow.loadURL(url).catch(console.error);
  updateTrayMenu();
});

ipcMain.handle('get-config', () => config);
ipcMain.handle('save-config', (event, newConfig) => {
  const prevServerUrl = config.serverUrl;
  config = { ...config, ...newConfig };
  saveConfig(config);
  startScheduledScan();
  // Reload the workbench if server URL changed
  if (newConfig.serverUrl && newConfig.serverUrl !== prevServerUrl && mainWindow) {
    mainWindow.loadURL(newConfig.serverUrl).catch(console.error);
  }
  if (clipboardMonitor) {
    clipboardMonitor.refreshConfig(true).catch(() => {});
  }
  updateTrayMenu();
  return config;
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
  clipboardMonitor = createClipboardMonitor({
    getBackendUrl: () => config.backendUrl || config.serverUrl,
    getAuthState: () => authState,
    getClientToken: () => resolveClientIngressToken(),
    getCompanyId: () => resolveReportCompanyId(),
  });
  clipboardMonitor.start().catch(() => {});
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
  if (clipboardMonitor) {
    clipboardMonitor.stop();
  }
});
