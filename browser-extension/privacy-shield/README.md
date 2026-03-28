# AegisAI Privacy Shield Extension

## Features

- Monitor input on major AI websites: ChatGPT, 豆包, 文心一言
- Detect sensitive content by regex and optional `/predict` service
- Floating warning bar with:
  - 检测到敏感信息，是否脱敏后发送？
  - 一键脱敏
  - 忽略警告（并记录日志）
- Dynamic selector/config update from backend `/api/privacy/config/public`
- Event reporting to backend `/api/privacy/events`

## Install (Chrome/Edge)

1. Open Extensions page and enable developer mode.
2. Click Load unpacked.
3. Choose this folder: `browser-extension/privacy-shield`.

## Install (Firefox)

1. Open `about:debugging`.
2. Choose This Firefox.
3. Click Load Temporary Add-on.
4. Select `manifest.json` in this folder.

## Backend Dependencies

- Spring backend: `http://localhost:8080`
- Python predict endpoint: `http://localhost:5000/predict`

You can override these via backend privacy config (`/api/privacy/config`).
