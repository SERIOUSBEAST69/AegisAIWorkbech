/**
 * 浏览器历史扫描器
 *
 * 读取主流浏览器的历史记录（SQLite 数据库），识别用户访问过的AI服务网站。
 *
 * 支持：
 * - Google Chrome（Windows / macOS / Linux）
 * - Microsoft Edge（Windows / macOS）
 * - Mozilla Firefox（Windows / macOS / Linux）
 * - Brave（Windows / macOS）
 *
 * 注意：
 * 1. 部分浏览器在运行时会锁定历史数据库，需要复制后再读取。
 * 2. 使用 better-sqlite3（同步API）而非异步驱动，以简化逻辑。
 * 3. 仅读取，不修改任何浏览器数据。
 */

'use strict';

const path = require('path');
const fs   = require('fs');
const os   = require('os');
const { AI_SERVICES } = require('./aiServiceList');

// ── 历史记录数据库路径 ────────────────────────────────────────────────────────

function getBrowserHistoryPaths() {
  const platform = process.platform;
  const home = os.homedir();
  const paths = [];

  if (platform === 'win32') {
    const appdata = process.env.APPDATA || path.join(home, 'AppData', 'Roaming');
    const local   = process.env.LOCALAPPDATA || path.join(home, 'AppData', 'Local');

    // Chrome
    paths.push({
      browser: 'Chrome',
      type: 'chromium',
      path: path.join(local, 'Google', 'Chrome', 'User Data', 'Default', 'History'),
    });
    // Edge
    paths.push({
      browser: 'Edge',
      type: 'chromium',
      path: path.join(local, 'Microsoft', 'Edge', 'User Data', 'Default', 'History'),
    });
    // Brave
    paths.push({
      browser: 'Brave',
      type: 'chromium',
      path: path.join(local, 'BraveSoftware', 'Brave-Browser', 'User Data', 'Default', 'History'),
    });
    // Firefox
    const ffProfileDir = path.join(appdata, 'Mozilla', 'Firefox', 'Profiles');
    if (fs.existsSync(ffProfileDir)) {
      for (const profile of fs.readdirSync(ffProfileDir)) {
        paths.push({
          browser: 'Firefox',
          type: 'firefox',
          path: path.join(ffProfileDir, profile, 'places.sqlite'),
        });
      }
    }

  } else if (platform === 'darwin') {
    // Chrome
    paths.push({
      browser: 'Chrome',
      type: 'chromium',
      path: path.join(home, 'Library', 'Application Support', 'Google', 'Chrome', 'Default', 'History'),
    });
    // Edge
    paths.push({
      browser: 'Edge',
      type: 'chromium',
      path: path.join(home, 'Library', 'Application Support', 'Microsoft Edge', 'Default', 'History'),
    });
    // Brave
    paths.push({
      browser: 'Brave',
      type: 'chromium',
      path: path.join(home, 'Library', 'Application Support', 'BraveSoftware', 'Brave-Browser', 'Default', 'History'),
    });
    // Firefox
    const ffProfileDir = path.join(home, 'Library', 'Application Support', 'Firefox', 'Profiles');
    if (fs.existsSync(ffProfileDir)) {
      for (const profile of fs.readdirSync(ffProfileDir)) {
        paths.push({
          browser: 'Firefox',
          type: 'firefox',
          path: path.join(ffProfileDir, profile, 'places.sqlite'),
        });
      }
    }

  } else {
    // Linux
    // Chrome
    paths.push({
      browser: 'Chrome',
      type: 'chromium',
      path: path.join(home, '.config', 'google-chrome', 'Default', 'History'),
    });
    // Chromium
    paths.push({
      browser: 'Chromium',
      type: 'chromium',
      path: path.join(home, '.config', 'chromium', 'Default', 'History'),
    });
    // Brave
    paths.push({
      browser: 'Brave',
      type: 'chromium',
      path: path.join(home, '.config', 'BraveSoftware', 'Brave-Browser', 'Default', 'History'),
    });
    // Firefox
    const ffProfileDir = path.join(home, '.mozilla', 'firefox');
    if (fs.existsSync(ffProfileDir)) {
      for (const profile of fs.readdirSync(ffProfileDir)) {
        if (profile.endsWith('.default') || profile.endsWith('.default-release')) {
          paths.push({
            browser: 'Firefox',
            type: 'firefox',
            path: path.join(ffProfileDir, profile, 'places.sqlite'),
          });
        }
      }
    }
  }

  return paths.filter(p => fs.existsSync(p.path));
}

// ── SQLite 读取 ───────────────────────────────────────────────────────────────

/**
 * 从 Chromium 历史 SQLite 数据库读取最近 URL。
 * 因为浏览器可能锁定文件，先复制到临时目录再读取。
 */
function readChromiumHistory(dbPath) {
  const tmpPath = path.join(os.tmpdir(), `aegis_hist_${Date.now()}.db`);
  try {
    fs.copyFileSync(dbPath, tmpPath);
    const Database = require('better-sqlite3');
    const db = new Database(tmpPath, { readonly: true, fileMustExist: true });
    // Limit to last 30 days and 1000 entries for performance
    const thirtyDaysAgo = (Date.now() - 30 * 24 * 60 * 60 * 1000 + 11644473600000) * 1000;
    const rows = db.prepare(
      'SELECT url, title, last_visit_time FROM urls WHERE last_visit_time > ? ORDER BY last_visit_time DESC LIMIT 1000'
    ).all(thirtyDaysAgo);
    db.close();
    return rows.map(r => ({
      url: r.url || '',
      title: r.title || '',
      // Chromium 时间是从 1601-01-01 开始的微秒数
      lastVisit: r.last_visit_time
        ? new Date(r.last_visit_time / 1000 - 11644473600000).toISOString()
        : null,
    }));
  } catch (e) {
    return [];
  } finally {
    try { if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath); } catch { /* ignore */ }
  }
}

/**
 * 从 Firefox places.sqlite 读取最近 URL。
 */
function readFirefoxHistory(dbPath) {
  const tmpPath = path.join(os.tmpdir(), `aegis_ff_${Date.now()}.db`);
  try {
    fs.copyFileSync(dbPath, tmpPath);
    const Database = require('better-sqlite3');
    const db = new Database(tmpPath, { readonly: true, fileMustExist: true });
    // Limit to last 30 days and 1000 entries for performance (Firefox uses microseconds)
    const thirtyDaysAgoUs = (Date.now() - 30 * 24 * 60 * 60 * 1000) * 1000;
    const rows = db.prepare(
      'SELECT url, title, last_visit_date FROM moz_places WHERE visit_count > 0 AND last_visit_date > ? ORDER BY last_visit_date DESC LIMIT 1000'
    ).all(thirtyDaysAgoUs);
    db.close();
    return rows.map(r => ({
      url: r.url || '',
      title: r.title || '',
      // Firefox 时间是微秒
      lastVisit: r.last_visit_date
        ? new Date(r.last_visit_date / 1000).toISOString()
        : null,
    }));
  } catch (e) {
    return [];
  } finally {
    try { if (fs.existsSync(tmpPath)) fs.unlinkSync(tmpPath); } catch { /* ignore */ }
  }
}

// ── 主扫描函数 ────────────────────────────────────────────────────────────────

/**
 * 扫描所有浏览器历史，返回访问过的AI服务列表。
 * @returns {object[]}
 */
function scanBrowserHistory() {
  const browserPaths = getBrowserHistoryPaths();
  const allUrls = [];

  for (const bp of browserPaths) {
    let records = [];
    try {
      if (bp.type === 'chromium') {
        records = readChromiumHistory(bp.path);
      } else if (bp.type === 'firefox') {
        records = readFirefoxHistory(bp.path);
      }
    } catch { /* ignore */ }
    allUrls.push(...records.map(r => ({ ...r, browser: bp.browser })));
  }

  const found = new Map(); // domain → result

  for (const service of AI_SERVICES) {
    for (const domain of service.domains) {
      // 对本地地址跳过浏览器历史扫描
      if (domain.startsWith('localhost') || domain.startsWith('127.0.0.1')) continue;

      const matchedRecord = allUrls.find(r =>
        r.url.includes(domain)
      );

      if (matchedRecord && !found.has(service.name)) {
        found.set(service.name, {
          name: service.name,
          domain,
          category: service.category,
          riskLevel: service.riskLevel,
          source: 'browser_history',
          description: service.description,
          browser: matchedRecord.browser,
          lastSeen: matchedRecord.lastVisit || new Date().toISOString(),
        });
        break;
      }
    }
  }

  return [...found.values()];
}

module.exports = { scanBrowserHistory };
