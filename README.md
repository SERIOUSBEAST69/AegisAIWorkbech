
# AegisAI 前后端一体化启动说明

## ⚠️ 首次安装必须执行

### 重新安装前端依赖（修复图标丢失）
```bash
# 在项目根目录执行
npm install
```
> **原因**：`@element-plus/icons-vue` 包已添加到依赖中，`npm install` 后所有 UI 图标将恢复正常显示。

---

## 一、快速启动（本地开发 — H2 内存数据库）

本项目支持**无需安装 MySQL / Redis / Elasticsearch / RabbitMQ** 的本地开发模式，后端自动使用 H2 内存数据库并在缺少外部服务时优雅降级。

### 1. 启动后端（Spring Boot）
```bash
cd backend
mvn clean package -DskipTests
java -jar target/AegisAI-backend-0.1.0.jar
```
- API 文档：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console（JDBC URL: `jdbc:h2:mem:aegisai`）

> **注意**：无需启动 RabbitMQ、Redis、Elasticsearch，后端会优雅降级。

### 2. 启动前端（Vue + Vite）
```bash
npm install      # 项目根目录（必须先执行，确保 @element-plus/icons-vue 已安装）
npm run dev
```
- 访问：http://localhost:5173
- 默认账号：`admin` / `admin`

### 3. 启动 Python AI 推理服务
```bash
cd python-service
pip install -r requirements.txt
python app.py
```
- 首次启动时若无模型文件，会自动运行 `gen_behavior_data.py` 和 `train_anomaly.py` 完成训练（约需 30–120 秒）。
- 如果自动训练失败，手动执行：
  ```bash
  python gen_behavior_data.py
  python train_anomaly.py
  python app.py
  ```
- 服务地址：http://localhost:5000
- 健康检查：http://localhost:5000/health

> **注意**：不启动 Python 服务时，AI 风险评级页面会使用内置 mock 数据；异常检测页面会显示"❌ 推理服务不可用"提示。

### 4. 启动 Electron 客户端（可选）
```bash
cd electron
npm install
npm start
```
- 客户端默认连接 `http://localhost:5173`（Vue 开发服务器），打包后需手动配置服务端地址。

---

## 二、威胁监控模拟器
```bash
cd python-service
python openclaw_simulator.py --count 1200 --url http://localhost:8080
```
> 上报接口 `/api/security/events/report` 无需登录 token，可直接调用。前提是 Spring Boot 后端已启动。

---

## 三、完整生产部署（Docker Compose）
```bash
cp backend/src/main/resources/docker-compose.yml .
docker compose up -d
```
Docker Compose 会自动启动：MySQL、Redis、Elasticsearch、RabbitMQ、Spring Boot 后端、Python 推理服务。

---

## 四、环境变量（覆盖默认值）

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `AI_INFERENCE_URL` | `http://localhost:5000` | Python 推理服务地址 |
| `ELASTICSEARCH_URIS` | `http://localhost:9200` | Elasticsearch 地址 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ 主机 |
| `CROSS_SITE_ALLOWED_ORIGINS` | `http://localhost:5173,...` | 允许的前端来源 |

Docker Compose 生产部署时，将上述变量设置为对应容器名（如 `AI_INFERENCE_URL=http://ai-inference:5000`）。

---

## 五、常见问题

**Q: 网站图标全部丢失（菜单图标、按钮图标、Favicon 不显示）**
- 执行 `npm install`（项目根目录），安装新增的 `@element-plus/icons-vue` 包后重启开发服务器。

**Q: 后端日志报 RabbitMQ 连接失败 / 无法启动**
- 已修复：`auto-startup: false` 配置确保 RabbitMQ 不可用时后端仍可正常启动，消息队列功能降级。
- 如需完整功能，请启动 RabbitMQ：`docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`

**Q: 后端日志报 Redis 连接失败**
- 本地开发可忽略此警告，限流功能降级为放行。
- 生产环境请安装 Redis 并设置 `REDIS_HOST`。

**Q: 后端日志报 Elasticsearch 连接失败**
- 本地开发可忽略，审计日志 ES 全文检索不可用，但数据库层仍可正常查询。

**Q: AI 风险评级 / 异常检测显示"推理服务不可用"**
- 确认 Python 推理服务已启动（`cd python-service && python app.py`）且监听 `http://localhost:5000`。
- 首次运行需先训练模型：`python gen_behavior_data.py && python train_anomaly.py`

**Q: 首页控制台报 `No static resource api/security/cross-site/status`**
- 确认 Spring Boot 后端已成功启动（访问 http://localhost:8080/swagger-ui.html 验证）。
- 此接口无需登录，若后端正常运行则不会报 404。

**Q: Electron 客户端显示 `{"code":40100,"msg":"未登录或令牌失效"}`**
- 已修复：客户端配置迁移逻辑现在会自动检测并修正指向后端 API 的错误 serverUrl。
- 删除 Electron userData 目录下的 `config.json` 可强制重置：
  - Windows: `%APPDATA%\aegis-workbench\config.json`
  - macOS: `~/Library/Application Support/aegis-workbench/config.json`
  - Linux: `~/.config/aegis-workbench/config.json`

**Q: Electron 托盘图标空白**
- 已修复：tray 图标现在会按平台最优尺寸（macOS: 22×22，其他: 32×32）自动缩放。

---

如需扩展更多功能，请参考 `controller/service/entity/mapper` 目录结构补充业务代码。

