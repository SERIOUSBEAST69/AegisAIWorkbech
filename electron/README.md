# Aegis 轻量级守护客户端

适用于中小企业的终端监控客户端，用于实时检测员工设备上未经授权的"影子AI"服务，防止核心数据与隐私泄露。

## 功能特性

| 功能 | 说明 |
|------|------|
| **影子AI自动发现** | 自动识别员工私自注册/使用的 ChatGPT、Midjourney、Claude 等AI服务 |
| **多维扫描引擎** | 浏览器历史 + 网络连接 + 本地进程，三管齐下 |
| **实时上报** | 发现结果自动上报至 Aegis 服务端，管理员在工作台即可查看 |
| **高风险告警** | 发现高风险影子AI时推送系统通知 |
| **隐私盾剪贴板监控** | 检测剪贴板敏感信息，仅在活动窗口属于 AI 应用时告警并上报 |
| **轻量托盘运行** | 最小化到系统托盘，常驻后台不打扰用户 |
| **开机自启** | 可配置为系统启动时自动运行 |
| **跨平台支持** | Windows 10/11、macOS 12+、Ubuntu 20.04+ |

---

## 快速开始

### 一、前提条件

- Node.js 18+（仅开发/构建时需要）
- Aegis 服务端已部署并可访问

### 二、安装依赖

```bash
cd electron
npm install
```

### 三、开发运行（连接本地服务端）

```bash
# 同时启动前端开发服务器
cd ..
npm run dev &

# 启动 Electron 客户端（连接 localhost:5173）
cd electron
AEGIS_DEV_URL=http://localhost:5173 npm start
```

### 四、连接生产服务端

修改客户端配置文件（首次运行后会自动生成）：

- **Windows**：`%APPDATA%\aegis-client\config.json`
- **macOS**：`~/Library/Application Support/aegis-client/config.json`
- **Linux**：`~/.config/aegis-client/config.json`

```json
{
  "serverUrl": "http://your-server-ip:8080",
  "scanIntervalMinutes": 30,
  "autoStart": true,
  "minimizeToTray": true
}
```

也可以在托盘菜单 → **服务器设置** 中修改。

---

## 打包发布

### Windows 安装包（NSIS）

```bash
npm run build:win
```

生成 `dist/Aegis 客户端 Setup 1.0.0.exe`，支持一键安装、创建桌面/开始菜单快捷方式。

### macOS DMG

```bash
npm run build:mac
```

支持 Intel（x64）和 Apple Silicon（arm64）双架构。

### Linux DEB/RPM

```bash
npm run build:linux
```

---

## 影子AI检测范围

### 国际AI服务

| 服务 | 检测方式 |
|------|----------|
| ChatGPT / OpenAI | 浏览器历史、网络连接 |
| Claude / Anthropic | 浏览器历史、网络连接 |
| Gemini / Google AI | 浏览器历史、网络连接 |
| Midjourney | 浏览器历史 |
| GitHub Copilot | 网络连接（IDE插件流量） |
| Cursor | 进程检测、网络连接 |
| Perplexity AI | 浏览器历史 |
| ... | 共 35+ 个服务 |

### 国内AI服务

| 服务 | 检测方式 |
|------|----------|
| 文心一言（百度） | 浏览器历史、网络连接 |
| 讯飞星火 | 浏览器历史、网络连接 |
| 通义千问（阿里） | 浏览器历史、网络连接 |
| Kimi（月之暗面） | 浏览器历史、网络连接 |
| 豆包（字节跳动） | 浏览器历史、进程、网络连接 |
| DeepSeek | 浏览器历史、网络连接 |
| 智谱清言 / ChatGLM | 浏览器历史、网络连接 |
| ... | 共 10+ 个服务 |

### 本地AI工具（进程检测）

| 工具 | 说明 |
|------|------|
| Ollama | 本地大模型运行框架 |
| LM Studio | 本地LLM图形界面工具 |
| GPT4All | 离线AI对话工具 |
| LocalAI | 开源本地AI服务 |
| Cursor | AI代码编辑器 |
| Jan | 开源AI对话桌面应用 |

---

## 企业批量部署

### 方案一：MSI/PKG 静默安装（推荐）

1. 打包前在 `electron/package.json` 的 `build.nsis` 中配置默认服务器地址
2. 通过企业 MDM（Microsoft Intune / Jamf）推送安装
3. 安装后自动注册并开始扫描

### 方案二：脚本批量安装（Windows PowerShell）

```powershell
# 设置服务端地址，再运行安装包
$env:AEGIS_SERVER_URL = "http://192.168.1.100:8080"
Start-Process "AegisSetup.exe" -ArgumentList "/S" -Wait
```

### 方案三：Docker 容器化（适合服务器节点）

```bash
docker run -d \
  -e AEGIS_SERVER_URL=http://your-server:8080 \
  -e AEGIS_CLIENT_ID=$(hostname) \
  aegisai/client:latest
```

---

## 隐私说明

Aegis 客户端遵循**最小权限**原则：

- **只读**访问浏览器历史，不修改任何数据
- 仅读取浏览器访问过的域名，不读取页面内容、密码、Cookie
- 进程扫描仅获取进程名称，不读取进程内存
- 所有数据仅上报至企业自己部署的 Aegis 服务端，不经过第三方

剪贴板监控误报抑制策略：

- 仅当活动窗口匹配 AI 应用规则（窗口标题或进程名）时触发系统通知
- 若活动窗口为 Word、记事本等非 AI 应用，只记录审计事件，不弹告警
- 相同内容在 1 分钟内只告警一次（哈希去重）

---

## 提供给管理员的信息

要让我完成企业级的完整交付，您需要提供：

1. **服务端部署环境**：服务器 IP/域名、端口、SSL 证书（如需 HTTPS）
2. **终端设备系统**：员工主要使用 Windows / macOS / Linux？各占多少？
3. **分发渠道**：是否有 MDM 系统（如 Intune、Jamf）？还是手动安装？
4. **网络环境**：是否内网隔离？是否需要代理设置？
5. **合规要求**：是否需要对员工公示监控范围（建议是）？
6. **告警通知**：高风险事件是否需要邮件/钉钉/企业微信推送？（需配置服务端）
