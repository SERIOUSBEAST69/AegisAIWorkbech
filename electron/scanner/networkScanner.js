/**
 * 网络连接扫描器
 *
 * 通过调用系统命令（netstat / ss）获取当前活跃网络连接，
 * 匹配已知AI服务域名或IP，识别正在进行的AI服务通信。
 *
 * 同时探测本机已知AI服务端口（Ollama、LM Studio等），
 * 以发现正在本地运行但无外部连接的AI工具。
 *
 * 注意：仅能识别当前时刻的活跃连接；历史连接需结合浏览器历史扫描。
 */

'use strict';

const { execSync } = require('child_process');
const net = require('net');
const { AI_SERVICES } = require('./aiServiceList');

/**
 * 获取系统当前活跃连接的外部主机名/IP列表。
 * @returns {string[]} 地址列表，如 ['chat.openai.com', '104.18.x.x', ...]
 */
function getActiveConnections() {
  const platform = process.platform;
  let output = '';

  try {
    if (platform === 'win32') {
      // Only established TCP connections
      output = execSync('netstat -nt 2>nul', { timeout: 5000 }).toString();
    } else if (platform === 'darwin') {
      output = execSync('netstat -an -p tcp 2>/dev/null', { timeout: 5000 }).toString();
    } else {
      // Linux – prefer ss (faster), fall back to netstat
      output = execSync('ss -tn state established 2>/dev/null || netstat -tn 2>/dev/null', { timeout: 5000 }).toString();
    }
  } catch (e) {
    // 命令失败时返回空列表
    return [];
  }

  const addresses = new Set();
  const lines = output.split('\n');

  for (const line of lines) {
    // 提取外部地址（IPv4）
    const ipMatch = line.match(/(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d+)/g);
    if (ipMatch) {
      ipMatch.forEach(addr => {
        const [ip] = addr.split(':');
        if (ip && !isLocalAddress(ip)) {
          addresses.add(ip);
        }
      });
    }

    // 提取带主机名的地址（如 chat.openai.com:443）
    const hostMatch = line.match(/([a-zA-Z0-9][-a-zA-Z0-9.]+\.[a-zA-Z]{2,}):(\d+)/g);
    if (hostMatch) {
      hostMatch.forEach(addr => {
        const host = addr.split(':')[0];
        if (host) addresses.add(host);
      });
    }
  }

  return [...addresses];
}

function isLocalAddress(ip) {
  return ip === '127.0.0.1' || ip === '0.0.0.0' || ip.startsWith('192.168.') ||
    ip.startsWith('10.') || ip.startsWith('172.16.') || ip === '::1';
}

/**
 * 探测本机特定端口是否有服务监听（用于发现 Ollama、LM Studio 等本地AI工具）。
 * @param {string} host
 * @param {number} port
 * @param {number} timeoutMs
 * @returns {Promise<boolean>}
 */
function probeLocalPort(host, port, timeoutMs = 800) {
  return new Promise(resolve => {
    const socket = new net.Socket();
    let done = false;
    const finish = (result) => {
      if (!done) {
        done = true;
        socket.destroy();
        resolve(result);
      }
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
    socket.connect(port, host);
  });
}

/**
 * 提取服务域名中的本机端口（仅处理 localhost / 127.0.0.1 / ::1 格式）。
 * @param {string} domain  如 'localhost:11434'
 * @returns {{ host: string, port: number } | null}
 */
function parseLocalEndpoint(domain) {
  const m = domain.match(/^(localhost|127\.0\.0\.1|\[::1\]):(\d+)$/);
  if (!m) return null;
  return { host: '127.0.0.1', port: parseInt(m[2], 10) };
}

/**
 * 扫描网络连接（含本地端口探测），返回发现的AI服务列表。
 * @returns {Promise<object[]>}
 */
async function scanNetworkConnections() {
  const activeAddresses = getActiveConnections();
  const found = [];

  // ── 1. 外部连接匹配 ─────────────────────────────────────────────────────────
  for (const service of AI_SERVICES) {
    for (const domain of service.domains) {
      const endpoint = parseLocalEndpoint(domain);
      if (endpoint) continue; // 本地地址留给端口探测

      const domainHost = domain.split(':')[0];
      const matched = activeAddresses.find(addr =>
        addr === domainHost ||
        addr.endsWith('.' + domainHost) ||
        domainHost.endsWith('.' + addr)
      );

      if (matched) {
        found.push({
          name: service.name,
          domain,
          category: service.category,
          riskLevel: service.riskLevel,
          source: 'network',
          description: service.description,
          matchedAddress: matched,
          lastSeen: new Date().toISOString(),
        });
        break;
      }
    }
  }

  // ── 2. 本地端口探测（Ollama、LM Studio、LocalAI 等） ───────────────────────
  const localProbes = [];
  for (const service of AI_SERVICES) {
    for (const domain of service.domains) {
      const endpoint = parseLocalEndpoint(domain);
      if (!endpoint) continue;
      // 避免重复探测
      const alreadyFound = found.some(f => f.name === service.name);
      if (!alreadyFound) {
        localProbes.push({ service, domain, endpoint });
      }
    }
  }

  if (localProbes.length > 0) {
    const results = await Promise.all(
      localProbes.map(async ({ service, domain, endpoint }) => {
        const open = await probeLocalPort(endpoint.host, endpoint.port);
        return open ? { service, domain } : null;
      })
    );

    for (const hit of results) {
      if (!hit) continue;
      const { service, domain } = hit;
      // 去重：同一服务可能有多个本地端口
      if (!found.some(f => f.name === service.name)) {
        found.push({
          name: service.name,
          domain,
          category: service.category,
          riskLevel: service.riskLevel,
          source: 'network',
          description: service.description,
          matchedAddress: domain,
          lastSeen: new Date().toISOString(),
        });
      }
    }
  }

  return found;
}

module.exports = { scanNetworkConnections };
