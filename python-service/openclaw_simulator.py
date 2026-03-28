#!/usr/bin/env python3
"""
OpenClaw 工作台事件模拟器（Workbench Event Simulator）
======================================================
职责定位：向 AegisAI 工作台后端上报模拟的安全事件，用于填充告警面板、
测试检测规则和可视化大屏。本文件专注于工作台集成，与后端 API 紧密耦合。

⚠️  注意：本文件仅负责工作台的事件数据生成与上报。
   如需运行攻防博弈推演（独立于工作台），请使用：
     python openclaw_adversarial.py --verbose --scenario supply_chain_apt

用法：
    python openclaw_simulator.py [--url URL] [--count N] [--batch BATCH_SIZE] [--delay SECONDS] [--token TOKEN] [--company-id ID]

参数：
    --url       AegisAI 后端地址，默认 http://localhost:8080
    --count     生成事件总数，默认 1200
    --batch     每批上报数量，默认 50
    --delay     每批之间的延迟秒数，默认 0.1（实时模式用 1-5）
    --token     客户端上报令牌（可选，支持 AEGIS_CLIENT_TOKEN 环境变量）
    --company-id 上报公司ID（默认 1，支持 AEGIS_COMPANY_ID 环境变量）
    --realtime  实时模式：每秒生成 1-5 条事件，持续运行直到 Ctrl+C

示例：
    # 一次性生成 1200 条历史数据
    python openclaw_simulator.py --count 1200

    # 实时模拟（测试告警功能）
    python openclaw_simulator.py --realtime
"""

import argparse
import json
import os
import random
import time
import uuid
from datetime import datetime, timedelta
from typing import Optional, Tuple
import urllib.request
import urllib.error

# ─────────────────────────── 模拟数据配置 ───────────────────────────────────

EMPLOYEES = [
    {"id": "emp_001", "hostname": "WIN-WS-ZHANG"},
    {"id": "emp_002", "hostname": "WIN-WS-LI"},
    {"id": "emp_003", "hostname": "MAC-DESK-WANG"},
    {"id": "emp_004", "hostname": "WIN-LAPTOP-ZHAO"},
    {"id": "emp_005", "hostname": "LINUX-DEV-CHEN"},
    {"id": "emp_006", "hostname": "WIN-WS-LIU"},
    {"id": "emp_007", "hostname": "MAC-BOOK-YANG"},
    {"id": "emp_008", "hostname": "WIN-WS-HUANG"},
    {"id": "emp_009", "hostname": "WIN-LAPTOP-WU"},
    {"id": "emp_010", "hostname": "LINUX-SERVER-ZHOU"},
]

SENSITIVE_FILES = [
    # 财务文档
    "/Documents/财务报表_2025Q4.xlsx",
    "/Documents/员工薪资表_2025.xlsx",
    "/Desktop/年度审计报告.pdf",
    "/Documents/预算规划_机密.docx",
    # 客户数据
    "/Documents/客户联系人名单.csv",
    "/Desktop/VIP客户合同.pdf",
    "/Documents/CRM导出_全量.csv",
    # 源代码
    "/code/aegisai-core/src/config.properties",
    "/project/backend/src/main/resources/application.yml",
    "/source/payment-gateway/.env",
    "/source/payment-gateway/config/database.yml",
    # 数据库备份
    "/backup/aegisai_prod_20250314.sql",
    "/export/user_data_dump.bak",
    "/archive/transactions_2025.tar.gz",
    # 证书/密钥
    "/certs/server.pem",
    "/keys/api_private.pfx",
    "/config/.ssh/id_rsa",
    # 设计/知识产权
    "/Documents/产品路线图_2026_保密.pptx",
    "/Desktop/专利申请草案.docx",
    "/Documents/竞品分析_内部.key",
    # 人事资料
    "/HR/人员花名册_全量.xlsx",
    "/HR/绩效考核结果_2025.xlsx",
    "/HR/离职人员名单.csv",
]

TARGET_ADDRS = [
    "http://198.51.100.23:8888/upload",    # 可疑外部服务器
    "https://file-share.suspicious-domain.com/api/v2/push",
    "ftp://203.0.113.45:21/incoming/",
    "http://10.10.10.199:9999/steal",      # 内网恶意节点
    "https://pastebin-mirror.net/api/post",
    "http://185.220.101.47:80/drop",
    "sftp://anonymous@93.184.216.34/pub/",
    "http://c2-server.attacker-lab.com:4444/exfil",
]

EVENT_TYPES = [
    "FILE_STEAL",
    "SUSPICIOUS_UPLOAD",
    "BATCH_COPY",
    "EXFILTRATION",
    "DATA_SCRAPE",
    "CREDENTIAL_DUMP",
]

SEVERITY_WEIGHTS = {
    "critical": 0.15,
    "high": 0.30,
    "medium": 0.35,
    "low": 0.20,
}


def weighted_choice(weights: dict) -> str:
    items = list(weights.keys())
    probs = list(weights.values())
    r = random.random()
    cumulative = 0
    for item, prob in zip(items, probs):
        cumulative += prob
        if r <= cumulative:
            return item
    return items[-1]


def generate_event(offset_seconds: Optional[int] = None) -> dict:
    """生成一条模拟安全事件"""
    employee = random.choice(EMPLOYEES)
    file_path = random.choice(SENSITIVE_FILES)

    # 推断 Windows 或 Unix 路径风格
    if employee["hostname"].startswith("WIN"):
        file_path = "C:" + file_path.replace("/", "\\")
    elif employee["hostname"].startswith("MAC"):
        file_path = "/Users/" + employee["id"] + file_path

    severity = weighted_choice(SEVERITY_WEIGHTS)
    event_type = random.choice(EVENT_TYPES)

    # 文件大小（字节），critical/high 事件倾向于更大的文件
    size_base = {"critical": 10_000_000, "high": 2_000_000, "medium": 500_000, "low": 100_000}
    file_size = random.randint(size_base[severity] // 4, size_base[severity] * 3)

    if offset_seconds is not None:
        event_dt = datetime.now() - timedelta(seconds=offset_seconds)
    else:
        event_dt = datetime.now()

    return {
        "eventType": event_type,
        "filePath": file_path,
        "targetAddr": random.choice(TARGET_ADDRS),
        "employeeId": employee["id"],
        "hostname": employee["hostname"],
        "fileSize": file_size,
        "severity": severity,
        "status": "pending",
        "source": "openclaw-sim",
        "eventTime": event_dt.strftime("%Y-%m-%d %H:%M:%S"),
    }


def report_events(backend_url: str, events: list, client_token: str = "", company_id: Optional[int] = None) -> Tuple[int, int]:
    """将事件列表逐条上报至后端，返回 (成功数, 失败数)"""
    success = 0
    failure = 0
    report_url = f"{backend_url.rstrip('/')}/api/security/events/report"

    for event in events:
        payload = json.dumps(event).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if client_token:
            headers["X-Client-Token"] = client_token
        if company_id is not None and company_id > 0:
            headers["X-Company-Id"] = str(company_id)
        req = urllib.request.Request(
            report_url,
            data=payload,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                body = resp.read().decode("utf-8", errors="replace")
                try:
                    data = json.loads(body)
                    if data.get("code") == 20000:
                        success += 1
                    else:
                        print(f"[WARN] Server code={data.get('code')}: {data.get('msg')}")
                        failure += 1
                except json.JSONDecodeError:
                    # Non-JSON response; treat as success if HTTP status was 2xx
                    success += 1
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print(f"[ERROR] HTTP {e.code}: {body[:200]}")
            failure += 1
        except Exception as e:
            print(f"[ERROR] {e}")
            failure += 1

    return success, failure


def run_batch_mode(backend_url: str, total: int, batch_size: int, delay: float, client_token: str = "", company_id: Optional[int] = None):
    """一次性生成 total 条历史事件并分批上报"""
    print(f"[OpenClaw Simulator] 准备生成 {total} 条模拟事件，上报至 {backend_url}")
    print(f"  批次大小: {batch_size}  批间延迟: {delay}s\n")

    generated = 0
    ok_total = 0
    fail_total = 0

    # 模拟过去 30 天内的事件（时间从最久远到最近，带随机抖动）
    time_span_seconds = 30 * 24 * 3600
    offsets = sorted(random.randint(0, time_span_seconds) for _ in range(total))
    offsets.reverse()  # 最久远的先生成

    batch = []
    for i, offset in enumerate(offsets, 1):
        batch.append(generate_event(offset_seconds=offset))
        if len(batch) >= batch_size or i == total:
            ok, fail = report_events(backend_url, batch, client_token=client_token, company_id=company_id)
            ok_total += ok
            fail_total += fail
            generated += len(batch)
            print(f"  进度 {generated}/{total}  ✓ {ok_total}  ✗ {fail_total}")
            batch = []
            if delay > 0:
                time.sleep(delay)

    print(f"\n[完成] 共上报 {ok_total} 条成功，{fail_total} 条失败。")


def run_realtime_mode(backend_url: str, events_per_second: float = 2.0, client_token: str = "", company_id: Optional[int] = None):
    """实时模式：持续生成事件直到 Ctrl+C，模拟在线检测场景"""
    print(f"[OpenClaw Simulator] 实时模式启动，上报至 {backend_url}")
    print("  按 Ctrl+C 停止\n")

    total_ok = 0
    total_fail = 0
    interval = 1.0 / events_per_second

    try:
        while True:
            event = generate_event()
            ok, fail = report_events(backend_url, [event], client_token=client_token, company_id=company_id)
            total_ok += ok
            total_fail += fail
            ts = datetime.now().strftime("%H:%M:%S")
            status_icon = "✓" if ok else "✗"
            print(
                f"[{ts}] {status_icon} [{event['severity'].upper():8s}] "
                f"{event['eventType']} | {event['hostname']} | {event['filePath'][:40]}"
            )
            time.sleep(interval)
    except KeyboardInterrupt:
        print(f"\n[停止] 共上报 {total_ok} 条成功，{total_fail} 条失败。")


def parse_company_id(raw: str, fallback: int = 1) -> int:
    try:
        value = int(str(raw).strip())
        return value if value > 0 else fallback
    except (TypeError, ValueError):
        return fallback


def main():
    parser = argparse.ArgumentParser(
        description="OpenClaw 代理窃取模拟器 — 生成模拟安全事件并上报到 AegisAI"
    )
    parser.add_argument("--url", default="http://localhost:8080", help="AegisAI 后端地址")
    parser.add_argument("--count", type=int, default=1200, help="生成事件总数（批量模式）")
    parser.add_argument("--batch", type=int, default=50, help="每批上报数量")
    parser.add_argument("--delay", type=float, default=0.05, help="批间延迟（秒）")
    parser.add_argument("--realtime", action="store_true", help="实时模式（持续生成，Ctrl+C 停止）")
    parser.add_argument("--rps", type=float, default=2.0, help="实时模式每秒事件数（默认 2）")
    parser.add_argument("--token", default=os.getenv("AEGIS_CLIENT_TOKEN", ""), help="客户端上报令牌（可选）")
    parser.add_argument("--company-id", type=int, default=parse_company_id(os.getenv("AEGIS_COMPANY_ID", "1")), help="公司ID（默认 1）")
    args = parser.parse_args()

    if args.realtime:
        run_realtime_mode(args.url, events_per_second=args.rps, client_token=args.token, company_id=args.company_id)
    else:
        run_batch_mode(args.url, total=args.count, batch_size=args.batch, delay=args.delay, client_token=args.token, company_id=args.company_id)


if __name__ == "__main__":
    main()
