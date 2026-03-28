"""
gen_behavior_data.py — 员工 AI 行为合成数据生成器
====================================================
生成 1200 条模拟不同岗位员工使用 AI 服务的行为记录，
用于训练异常检测模型。

数据特征维度：
  - employee_id      员工ID
  - department       部门（研发/销售/HR/法务/财务）
  - ai_service       使用的AI服务名称
  - hour_of_day      操作小时（0-23）
  - day_of_week      星期几（0=周一）
  - message_length   消息文本长度
  - topic_code       话题类型编码（0=代码, 1=文档, 2=数据分析, 3=沟通, 4=其他）
  - session_duration_min  会话时长（分钟）
  - is_new_service   是否首次访问该AI（0/1）
  - is_anomaly       是否为异常行为（0=正常, 1=异常），用于有监督评估

异常检测规则出处（权威来源）：
  [1] MITRE ATT&CK T1048 — Exfiltration Over Alternative Protocol
      https://attack.mitre.org/techniques/T1048/
      涵盖：deep_night_code（深夜大量外发代码）、massive_data_dump（大批量数据粘贴至 AI）
  [2] MITRE ATT&CK T1530 — Data from Cloud Storage
      https://attack.mitre.org/techniques/T1530/
      涵盖：massive_data_dump（批量导出云端数据后上传 AI）
  [3] MITRE ATT&CK T1071 — Application Layer Protocol
      https://attack.mitre.org/techniques/T1071/
      涵盖：sudden_new_ai（绕过管控使用未经审批的 AI 服务）
  [4] MITRE ATT&CK T1078 — Valid Accounts（内部人员滥用合法凭据）
      https://attack.mitre.org/techniques/T1078/
      涵盖：credential_harvest（将认证凭据发送给外部 AI）
  [5] NIST SP 800-137 — Information Security Continuous Monitoring (ISCM)
      https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-137.pdf
      涵盖：weekend_spike（周末异常使用监控）
  [6] NIST SP 800-92 — Guide to Computer Security Log Management
      https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-92.pdf
      涵盖：所有时间异常检测（非工作时段行为）
  [7] SANS Institute — Insider Threat Detection Strategies
      https://www.sans.org/white-papers/36942/
      涵盖：repeated_sensitive_query（反复查询敏感话题）
  [8] MITRE ATLAS ML-Attack-0000 — Model Evasion via Adversarial Input
      https://atlas.mitre.org/
      涵盖：massive_data_dump（利用 AI 做数据分析以规避 DLP）

运行方法：
    cd python-service
    python gen_behavior_data.py

输出：behavior_data.csv（同目录）
"""

import csv
import os
import random
from pathlib import Path

random.seed(2024)

# ── 员工岗位配置 ──────────────────────────────────────────────────────────────
# 每个岗位有其典型的工作时段、偏好AI服务和话题分布
DEPARTMENTS = {
    "研发": {
        "count": 80,           # 模拟员工数量（用于随机分配员工ID）
        "work_hours": (9, 19), # 正常工作时段
        "ai_services": ["ChatGPT", "GitHub Copilot", "通义千问", "文心一言"],
        "ai_weights": [0.35, 0.35, 0.15, 0.15],
        "topic_weights": [0.55, 0.15, 0.15, 0.05, 0.10],  # 代码/文档/数据分析/沟通/其他
        "avg_msg_len": 300,
        "msg_len_std": 200,
        "avg_session_min": 12,
    },
    "销售": {
        "count": 50,
        "work_hours": (8, 18),
        "ai_services": ["ChatGPT", "文心一言", "豆包", "Kimi"],
        "ai_weights": [0.30, 0.30, 0.25, 0.15],
        "topic_weights": [0.05, 0.30, 0.15, 0.40, 0.10],
        "avg_msg_len": 150,
        "msg_len_std": 100,
        "avg_session_min": 6,
    },
    "HR": {
        "count": 20,
        "work_hours": (9, 18),
        "ai_services": ["文心一言", "豆包", "通义千问", "ChatGPT"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.03, 0.50, 0.10, 0.30, 0.07],
        "avg_msg_len": 180,
        "msg_len_std": 120,
        "avg_session_min": 8,
    },
    "法务": {
        "count": 15,
        "work_hours": (9, 18),
        "ai_services": ["Claude", "ChatGPT", "文心一言", "Kimi"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.02, 0.60, 0.10, 0.20, 0.08],
        "avg_msg_len": 400,
        "msg_len_std": 250,
        "avg_session_min": 15,
    },
    "财务": {
        "count": 25,
        "work_hours": (9, 18),
        "ai_services": ["通义千问", "文心一言", "豆包", "ChatGPT"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.05, 0.35, 0.40, 0.10, 0.10],
        "avg_msg_len": 200,
        "msg_len_std": 150,
        "avg_session_min": 7,
    },
}

KNOWN_AI_SERVICES = [
    "ChatGPT", "Claude", "Gemini", "GitHub Copilot",
    "文心一言", "通义千问", "豆包", "Kimi",
    "Perplexity", "Ollama",  # 最后两个是"异常"服务（本地或小众）
]

TOPIC_NAMES = ["代码", "文档", "数据分析", "沟通", "其他"]


def clamp(val, lo, hi):
    return max(lo, min(hi, val))


def gen_normal_record(emp_id: str, dept: str, dept_cfg: dict) -> dict:
    """生成一条正常的 AI 使用记录。"""
    work_start, work_end = dept_cfg["work_hours"]

    # 正常时段偏向工作小时（高斯分布）
    hour = int(clamp(
        random.gauss((work_start + work_end) / 2, 2),
        work_start, work_end
    ))
    day_of_week = random.choices(range(7), weights=[0.20, 0.20, 0.20, 0.20, 0.15, 0.03, 0.02])[0]

    ai_service = random.choices(dept_cfg["ai_services"], weights=dept_cfg["ai_weights"])[0]
    topic_code = random.choices(range(5), weights=dept_cfg["topic_weights"])[0]

    msg_len = clamp(
        int(random.gauss(dept_cfg["avg_msg_len"], dept_cfg["msg_len_std"])),
        20, 2000
    )
    session_min = clamp(
        int(random.gauss(dept_cfg["avg_session_min"], dept_cfg["avg_session_min"] * 0.5)),
        1, 60
    )

    return {
        "employee_id": emp_id,
        "department": dept,
        "ai_service": ai_service,
        "hour_of_day": hour,
        "day_of_week": day_of_week,
        "message_length": msg_len,
        "topic_code": topic_code,
        "session_duration_min": session_min,
        "is_new_service": 0,
        "is_anomaly": 0,
    }


def gen_anomaly_record(emp_id: str, dept: str, anomaly_type: str) -> dict:
    """
    生成一条异常 AI 使用记录。异常类型及其安全规则出处：

    late_night_heavy_code:
        深夜大量发送代码到外部 AI（数据泄露场景）。
        规则出处：MITRE ATT&CK T1048（Exfiltration Over Alternative Protocol）
        https://attack.mitre.org/techniques/T1048/
        SANS Insider Threat Detection Strategies §3.2（非工作时间大量数据外发）
        https://www.sans.org/white-papers/36942/

    sudden_new_ai:
        突然使用未经审批的新 AI 服务（绕过管控，影子 AI）。
        规则出处：MITRE ATT&CK T1071（Application Layer Protocol）
        https://attack.mitre.org/techniques/T1071/
        NIST SP 800-92（异常应用层流量日志分析）
        https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-92.pdf

    massive_data_dump:
        大量数据分析请求（财务/销售数据泄露场景，利用 AI 规避 DLP）。
        规则出处：MITRE ATT&CK T1030（Data Transfer Size Limits）
        https://attack.mitre.org/techniques/T1030/
        MITRE ATT&CK T1530（Data from Cloud Storage Object）
        https://attack.mitre.org/techniques/T1530/

    weekend_spike:
        周末密集使用 AI（异常加班/数据外发场景）。
        规则出处：NIST SP 800-137（ISCM，持续监控非工作时段异常）
        https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-137.pdf

    credential_harvest:
        将包含认证凭据的内容发送至外部 AI（密钥/密码泄露场景）。
        规则出处：MITRE ATT&CK T1078（Valid Accounts — credential misuse）
        https://attack.mitre.org/techniques/T1078/
        MITRE ATT&CK T1552（Unsecured Credentials）
        https://attack.mitre.org/techniques/T1552/

    repeated_sensitive_query:
        短时间内反复查询敏感话题（目标侦察）。
        规则出处：SANS Institute Insider Threat Detection §4 — Repeated Access Patterns
        https://www.sans.org/white-papers/36942/
        NIST SP 800-53 AU-12（Audit Record Generation — detect repeated access）
        https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-53r5.pdf
    """
    base = {
        "employee_id": emp_id,
        "department": dept,
        "is_anomaly": 1,
    }

    if anomaly_type == "late_night_heavy_code":
        # MITRE T1048: Exfiltration via AI chat (large code blocks sent late at night)
        base.update({
            "ai_service": random.choice(["ChatGPT", "GitHub Copilot", "Claude"]),
            "hour_of_day": random.choice(range(22, 24)) if random.random() > 0.5 else random.choice(range(0, 4)),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(800, 3000),   # 超长消息（大段代码）
            "topic_code": 0,  # 代码
            "session_duration_min": random.randint(30, 120),
            "is_new_service": 0,
        })
    elif anomaly_type == "sudden_new_ai":
        # MITRE T1071: Shadow AI — use of unapproved AI service to bypass DLP
        new_services = ["Ollama", "Perplexity", "Gemini"]  # 不在正常名单中
        base.update({
            "ai_service": random.choice(new_services),
            "hour_of_day": random.randint(9, 17),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(100, 500),
            "topic_code": random.randint(0, 4),
            "session_duration_min": random.randint(5, 30),
            "is_new_service": 1,  # 新服务
        })
    elif anomaly_type == "massive_data_dump":
        # MITRE T1030/T1530: Large-volume data paste into AI (finance/CRM exfiltration)
        base.update({
            "ai_service": random.choice(["ChatGPT", "Claude", "通义千问"]),
            "hour_of_day": random.randint(11, 15),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(1500, 5000),  # 超长（粘贴大量数据）
            "topic_code": 2,  # 数据分析
            "session_duration_min": random.randint(20, 90),
            "is_new_service": 0,
        })
    elif anomaly_type == "weekend_spike":
        # NIST SP 800-137: Anomalous off-hours AI activity detected by ISCM
        base.update({
            "ai_service": random.choice(["ChatGPT", "Claude", "文心一言"]),
            "hour_of_day": random.randint(10, 22),
            "day_of_week": random.choice([5, 6]),  # 周末
            "message_length": random.randint(200, 1000),
            "topic_code": random.randint(0, 4),
            "session_duration_min": random.randint(10, 60),
            "is_new_service": 0,
        })
    elif anomaly_type == "credential_harvest":
        # MITRE T1078/T1552: Employee pastes credentials/API keys into external AI
        base.update({
            "ai_service": random.choice(["ChatGPT", "Claude", "Gemini"]),
            "hour_of_day": random.randint(9, 18),
            "day_of_week": random.randint(0, 4),
            # Very long message: includes credential blocks (API keys, tokens, passwords)
            "message_length": random.randint(500, 2500),
            "topic_code": 0,  # 代码（包含密钥的代码段）
            "session_duration_min": random.randint(3, 20),
            "is_new_service": 0,
        })
    elif anomaly_type == "repeated_sensitive_query":
        # SANS Insider Threat §4: Rapid repeated queries on a sensitive topic (reconnaissance)
        base.update({
            "ai_service": random.choice(["ChatGPT", "Kimi", "通义千问"]),
            "hour_of_day": random.randint(8, 18),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(50, 300),    # Short but frequent queries
            "topic_code": random.choice([1, 2]),           # 文档 or 数据分析
            "session_duration_min": random.randint(1, 5),  # 很短（反复快速查询）
            "is_new_service": 0,
        })
    else:
        # fallback
        base.update({
            "ai_service": "ChatGPT",
            "hour_of_day": 2,
            "day_of_week": 1,
            "message_length": 2000,
            "topic_code": 0,
            "session_duration_min": 60,
            "is_new_service": 1,
        })

    return base


def generate_dataset(target_size: int = 1200) -> list:
    """生成完整数据集，正常记录占约 85%，异常记录占约 15%。

    异常类型覆盖 6 种不同场景（每种均有权威安全规则出处，见 gen_anomaly_record 文档）：
      - late_night_heavy_code（约 20%异常）：深夜大量发代码 [MITRE T1048]
      - sudden_new_ai       （约 20%异常）：突然使用新 AI [MITRE T1071]
      - massive_data_dump   （约 20%异常）：批量数据外发 [MITRE T1030/T1530]
      - weekend_spike       （约 15%异常）：周末密集使用 [NIST SP 800-137]
      - credential_harvest  （约 15%异常）：凭据泄露 [MITRE T1078/T1552]
      - repeated_sensitive_query（约10%异常）：反复敏感查询 [SANS Insider Threat]
    """
    records = []

    # ── 1. 为每个部门生成员工ID列表 ─────────────────────────────────────────
    emp_ids = {}
    for dept, cfg in DEPARTMENTS.items():
        emp_ids[dept] = [f"EMP_{dept[:1]}{i:04d}" for i in range(1, cfg["count"] + 1)]

    # ── 2. 正常记录（约 85%）─────────────────────────────────────────────────
    normal_target = int(target_size * 0.85)
    while len(records) < normal_target:
        dept = random.choice(list(DEPARTMENTS.keys()))
        emp = random.choice(emp_ids[dept])
        records.append(gen_normal_record(emp, dept, DEPARTMENTS[dept]))

    # ── 3. 异常记录（约 15%），各类型按比例分配 ──────────────────────────────
    # Weighted distribution ensures all 6 anomaly scenarios are represented.
    anomaly_types = [
        "late_night_heavy_code",     # 20%
        "sudden_new_ai",             # 20%
        "massive_data_dump",         # 20%
        "weekend_spike",             # 15%
        "credential_harvest",        # 15%
        "repeated_sensitive_query",  # 10%
    ]
    anomaly_weights = [0.20, 0.20, 0.20, 0.15, 0.15, 0.10]
    anomaly_target = target_size - normal_target
    for _ in range(anomaly_target):
        dept = random.choice(list(DEPARTMENTS.keys()))
        emp = random.choice(emp_ids[dept])
        atype = random.choices(anomaly_types, weights=anomaly_weights)[0]
        records.append(gen_anomaly_record(emp, dept, atype))

    # ── 4. 打乱顺序 ──────────────────────────────────────────────────────────
    random.shuffle(records)
    return records


if __name__ == "__main__":
    dataset = generate_dataset(target_size=1200)

    out_path = Path(__file__).parent / "behavior_data.csv"
    fieldnames = [
        "employee_id", "department", "ai_service",
        "hour_of_day", "day_of_week", "message_length",
        "topic_code", "session_duration_min",
        "is_new_service", "is_anomaly",
    ]

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(dataset)

    total = len(dataset)
    normal = sum(1 for r in dataset if r["is_anomaly"] == 0)
    anomaly = total - normal

    print(f"✅ 生成完成：{total} 条样本 → {out_path}")
    print(f"   正常记录：{normal} 条 ({normal/total*100:.1f}%)")
    print(f"   异常记录：{anomaly} 条 ({anomaly/total*100:.1f}%)")

    from collections import Counter
    dept_cnt = Counter(r["department"] for r in dataset)
    print("\n各部门样本分布：")
    for dept, cnt in sorted(dept_cnt.items()):
        print(f"  {dept:6s}: {cnt} 条")

    print("\n异常检测规则及出处（简要）：")
    rules = [
        ("late_night_heavy_code",    "MITRE ATT&CK T1048", "https://attack.mitre.org/techniques/T1048/"),
        ("sudden_new_ai",            "MITRE ATT&CK T1071", "https://attack.mitre.org/techniques/T1071/"),
        ("massive_data_dump",        "MITRE ATT&CK T1030", "https://attack.mitre.org/techniques/T1030/"),
        ("weekend_spike",            "NIST SP 800-137",    "https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-137.pdf"),
        ("credential_harvest",       "MITRE ATT&CK T1552", "https://attack.mitre.org/techniques/T1552/"),
        ("repeated_sensitive_query", "SANS Insider Threat","https://www.sans.org/white-papers/36942/"),
    ]
    for name, source, url in rules:
        print(f"  {name:28s} → {source}")
        print(f"      {url}")
DEPARTMENTS = {
    "研发": {
        "count": 80,           # 模拟员工数量（用于随机分配员工ID）
        "work_hours": (9, 19), # 正常工作时段
        "ai_services": ["ChatGPT", "GitHub Copilot", "通义千问", "文心一言"],
        "ai_weights": [0.35, 0.35, 0.15, 0.15],
        "topic_weights": [0.55, 0.15, 0.15, 0.05, 0.10],  # 代码/文档/数据分析/沟通/其他
        "avg_msg_len": 300,
        "msg_len_std": 200,
        "avg_session_min": 12,
    },
    "销售": {
        "count": 50,
        "work_hours": (8, 18),
        "ai_services": ["ChatGPT", "文心一言", "豆包", "Kimi"],
        "ai_weights": [0.30, 0.30, 0.25, 0.15],
        "topic_weights": [0.05, 0.30, 0.15, 0.40, 0.10],
        "avg_msg_len": 150,
        "msg_len_std": 100,
        "avg_session_min": 6,
    },
    "HR": {
        "count": 20,
        "work_hours": (9, 18),
        "ai_services": ["文心一言", "豆包", "通义千问", "ChatGPT"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.03, 0.50, 0.10, 0.30, 0.07],
        "avg_msg_len": 180,
        "msg_len_std": 120,
        "avg_session_min": 8,
    },
    "法务": {
        "count": 15,
        "work_hours": (9, 18),
        "ai_services": ["Claude", "ChatGPT", "文心一言", "Kimi"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.02, 0.60, 0.10, 0.20, 0.08],
        "avg_msg_len": 400,
        "msg_len_std": 250,
        "avg_session_min": 15,
    },
    "财务": {
        "count": 25,
        "work_hours": (9, 18),
        "ai_services": ["通义千问", "文心一言", "豆包", "ChatGPT"],
        "ai_weights": [0.35, 0.30, 0.20, 0.15],
        "topic_weights": [0.05, 0.35, 0.40, 0.10, 0.10],
        "avg_msg_len": 200,
        "msg_len_std": 150,
        "avg_session_min": 7,
    },
}

KNOWN_AI_SERVICES = [
    "ChatGPT", "Claude", "Gemini", "GitHub Copilot",
    "文心一言", "通义千问", "豆包", "Kimi",
    "Perplexity", "Ollama",  # 最后两个是"异常"服务（本地或小众）
]

TOPIC_NAMES = ["代码", "文档", "数据分析", "沟通", "其他"]


def clamp(val, lo, hi):
    return max(lo, min(hi, val))


def gen_normal_record(emp_id: str, dept: str, dept_cfg: dict) -> dict:
    """生成一条正常的 AI 使用记录。"""
    work_start, work_end = dept_cfg["work_hours"]

    # 正常时段偏向工作小时（高斯分布）
    hour = int(clamp(
        random.gauss((work_start + work_end) / 2, 2),
        work_start, work_end
    ))
    day_of_week = random.choices(range(7), weights=[0.20, 0.20, 0.20, 0.20, 0.15, 0.03, 0.02])[0]

    ai_service = random.choices(dept_cfg["ai_services"], weights=dept_cfg["ai_weights"])[0]
    topic_code = random.choices(range(5), weights=dept_cfg["topic_weights"])[0]

    msg_len = clamp(
        int(random.gauss(dept_cfg["avg_msg_len"], dept_cfg["msg_len_std"])),
        20, 2000
    )
    session_min = clamp(
        int(random.gauss(dept_cfg["avg_session_min"], dept_cfg["avg_session_min"] * 0.5)),
        1, 60
    )

    return {
        "employee_id": emp_id,
        "department": dept,
        "ai_service": ai_service,
        "hour_of_day": hour,
        "day_of_week": day_of_week,
        "message_length": msg_len,
        "topic_code": topic_code,
        "session_duration_min": session_min,
        "is_new_service": 0,
        "is_anomaly": 0,
    }


def gen_anomaly_record(emp_id: str, dept: str, anomaly_type: str) -> dict:
    """
    生成一条异常 AI 使用记录。异常类型包括：
      - late_night_heavy_code: 深夜大量发代码（研发泄密场景）
      - sudden_new_ai: 突然使用新的AI服务（绕过管控）
      - massive_data_dump: 大量数据分析请求（财务/销售数据泄露场景）
      - weekend_spike: 周末密集使用（异常加班场景）
    """
    base = {
        "employee_id": emp_id,
        "department": dept,
        "is_anomaly": 1,
    }

    if anomaly_type == "late_night_heavy_code":
        base.update({
            "ai_service": random.choice(["ChatGPT", "GitHub Copilot", "Claude"]),
            "hour_of_day": random.choice(range(22, 24)) if random.random() > 0.5 else random.choice(range(0, 4)),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(800, 3000),   # 超长消息（大段代码）
            "topic_code": 0,  # 代码
            "session_duration_min": random.randint(30, 120),
            "is_new_service": 0,
        })
    elif anomaly_type == "sudden_new_ai":
        new_services = ["Ollama", "Perplexity", "Gemini"]  # 不在正常名单中
        base.update({
            "ai_service": random.choice(new_services),
            "hour_of_day": random.randint(9, 17),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(100, 500),
            "topic_code": random.randint(0, 4),
            "session_duration_min": random.randint(5, 30),
            "is_new_service": 1,  # 新服务
        })
    elif anomaly_type == "massive_data_dump":
        base.update({
            "ai_service": random.choice(["ChatGPT", "Claude", "通义千问"]),
            "hour_of_day": random.randint(11, 15),
            "day_of_week": random.randint(0, 4),
            "message_length": random.randint(1500, 5000),  # 超长（粘贴大量数据）
            "topic_code": 2,  # 数据分析
            "session_duration_min": random.randint(20, 90),
            "is_new_service": 0,
        })
    elif anomaly_type == "weekend_spike":
        base.update({
            "ai_service": random.choice(["ChatGPT", "Claude", "文心一言"]),
            "hour_of_day": random.randint(10, 22),
            "day_of_week": random.choice([5, 6]),  # 周末
            "message_length": random.randint(200, 1000),
            "topic_code": random.randint(0, 4),
            "session_duration_min": random.randint(10, 60),
            "is_new_service": 0,
        })
    else:
        # fallback
        base.update({
            "ai_service": "ChatGPT",
            "hour_of_day": 2,
            "day_of_week": 1,
            "message_length": 2000,
            "topic_code": 0,
            "session_duration_min": 60,
            "is_new_service": 1,
        })

    return base


def generate_dataset(target_size: int = 1200) -> list:
    """生成完整数据集，正常记录占约 85%，异常记录占约 15%。"""
    records = []

    # ── 1. 为每个部门生成员工ID列表 ─────────────────────────────────────────
    emp_ids = {}
    for dept, cfg in DEPARTMENTS.items():
        emp_ids[dept] = [f"EMP_{dept[:1]}{i:04d}" for i in range(1, cfg["count"] + 1)]

    # ── 2. 正常记录（约 85%）─────────────────────────────────────────────────
    normal_target = int(target_size * 0.85)
    while len(records) < normal_target:
        dept = random.choice(list(DEPARTMENTS.keys()))
        emp = random.choice(emp_ids[dept])
        records.append(gen_normal_record(emp, dept, DEPARTMENTS[dept]))

    # ── 3. 异常记录（约 15%）─────────────────────────────────────────────────
    anomaly_types = [
        "late_night_heavy_code",
        "sudden_new_ai",
        "massive_data_dump",
        "weekend_spike",
    ]
    anomaly_target = target_size - normal_target
    for _ in range(anomaly_target):
        dept = random.choice(list(DEPARTMENTS.keys()))
        emp = random.choice(emp_ids[dept])
        atype = random.choice(anomaly_types)
        records.append(gen_anomaly_record(emp, dept, atype))

    # ── 4. 打乱顺序 ──────────────────────────────────────────────────────────
    random.shuffle(records)
    return records


if __name__ == "__main__":
    dataset = generate_dataset(target_size=1200)

    out_path = Path(__file__).parent / "behavior_data.csv"
    fieldnames = [
        "employee_id", "department", "ai_service",
        "hour_of_day", "day_of_week", "message_length",
        "topic_code", "session_duration_min",
        "is_new_service", "is_anomaly",
    ]

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(dataset)

    total = len(dataset)
    normal = sum(1 for r in dataset if r["is_anomaly"] == 0)
    anomaly = total - normal

    print(f"✅ 生成完成：{total} 条样本 → {out_path}")
    print(f"   正常记录：{normal} 条 ({normal/total*100:.1f}%)")
    print(f"   异常记录：{anomaly} 条 ({anomaly/total*100:.1f}%)")

    from collections import Counter
    dept_cnt = Counter(r["department"] for r in dataset)
    print("\n各部门样本分布：")
    for dept, cnt in sorted(dept_cnt.items()):
        print(f"  {dept:6s}: {cnt} 条")
