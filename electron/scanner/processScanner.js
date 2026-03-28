/**
 * 进程扫描器
 *
 * 检查当前系统正在运行的进程，识别本地 AI 工具
 * （如 Ollama、LM Studio、GPT4All、Cursor 等）。
 */

'use strict';

const { execSync } = require('child_process');
const { AI_SERVICES } = require('./aiServiceList');

/**
 * 获取当前运行进程名称列表。
 * @returns {string[]}
 */
function getRunningProcesses() {
  const platform = process.platform;
  let output = '';

  try {
    if (platform === 'win32') {
      output = execSync('tasklist /FO CSV /NH 2>nul', { timeout: 5000 }).toString();
      // 从 CSV 格式提取进程名
      return output.split('\n')
        .map(line => {
          const m = line.match(/"([^"]+)"/);
          return m ? m[1].replace('.exe', '').toLowerCase() : null;
        })
        .filter(Boolean);
    } else if (platform === 'darwin') {
      output = execSync('ps -axco command 2>/dev/null', { timeout: 5000 }).toString();
    } else {
      // Linux
      output = execSync('ps -axco comm 2>/dev/null', { timeout: 5000 }).toString();
    }
  } catch (e) {
    return [];
  }

  return output.split('\n').map(l => l.trim().toLowerCase()).filter(Boolean);
}

/**
 * 扫描运行中进程，返回发现的本地AI工具列表。
 * @returns {object[]}
 */
function scanRunningProcesses() {
  const processes = getRunningProcesses();
  const found = [];

  for (const service of AI_SERVICES) {
    if (!service.processNames || service.processNames.length === 0) continue;

    const matchedProcess = service.processNames.find(procName => {
      const lower = procName.toLowerCase().replace('.exe', '');
      return processes.some(p => p.includes(lower));
    });

    if (matchedProcess) {
      const primaryDomain = service.domains[0] || service.name.toLowerCase().replace(/\s+/g, '-');
      found.push({
        name: service.name,
        domain: primaryDomain,
        category: service.category,
        riskLevel: service.riskLevel,
        source: 'process',
        description: service.description,
        matchedProcess,
        lastSeen: new Date().toISOString(),
      });
    }
  }

  return found;
}

module.exports = { scanRunningProcesses };
