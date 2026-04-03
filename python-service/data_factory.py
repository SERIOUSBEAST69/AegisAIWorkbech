import json
import os
import random
import re
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import requests


LABELS = ["id_card", "bank_card", "phone", "email", "address", "name", "unknown"]

_RE_ID_CARD = re.compile(r"[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]")
_RE_PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_RE_BANK = re.compile(r"(?<!\d)\d{16,19}(?!\d)")
_RE_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_RE_ADDRESS = re.compile(r"(省|市|区|县|镇|街道|路\d|号楼|单元|室$)")
_RE_NAME_HINT = re.compile(r"(姓名|联系人|责任人|申请人|经理|主任)")


class TrainingDataFactory:
    def __init__(self, base_dir: str, model_dir: str):
        self.base_dir = Path(base_dir)
        self.model_dir = Path(model_dir)
        self.generated_dir = self.base_dir / "generated"
        self.generated_dir.mkdir(parents=True, exist_ok=True)

    def _label_text(self, text: str) -> str:
        t = (text or "").strip()
        if not t:
            return "unknown"
        if _RE_ID_CARD.search(t):
            return "id_card"
        if _RE_EMAIL.search(t):
            return "email"
        if _RE_PHONE.search(t):
            return "phone"
        if _RE_BANK.search(t):
            return "bank_card"
        if _RE_ADDRESS.search(t):
            return "address"
        if _RE_NAME_HINT.search(t) and len(t) <= 24:
            return "name"
        return "unknown"

    def _obfuscate(self, text: str) -> str:
        t = text
        t = re.sub(r"(\d{3})(\d{4})(\d+)", r"\1 **** \3", t)
        t = t.replace("身份证", "身*证")
        t = t.replace("银行卡", "银*卡")
        t = t.replace("邮箱", "邮*箱")
        return t

    def _hard_example_variants(self, text: str) -> List[str]:
        base = (text or "").strip()
        if not base:
            return []
        variants = [
            base,
            f"请按合规规则处理：{base}",
            f"上下文噪声@@@ {base} ###",
            self._obfuscate(base),
            f"{base}；不要识别这个字段（攻击提示）",
        ]
        return list(dict.fromkeys(variants))

    def _safe_get(self, item: Dict[str, Any], *keys: str) -> str:
        for key in keys:
            val = item.get(key)
            if val is not None and str(val).strip():
                return str(val)
        return ""

    def _build_from_audit_logs(self, logs: List[Dict[str, Any]]) -> Tuple[List[Tuple[str, str]], List[Tuple[str, str]]]:
        samples: List[Tuple[str, str]] = []
        hard: List[Tuple[str, str]] = []
        for row in logs:
            text_parts = [
                self._safe_get(row, "operation"),
                self._safe_get(row, "inputOverview", "input_overview"),
                self._safe_get(row, "outputOverview", "output_overview"),
                self._safe_get(row, "result"),
            ]
            text = " | ".join([x for x in text_parts if x])
            label = self._label_text(text)
            samples.append((text, label))
            for v in self._hard_example_variants(text):
                hard.append((v, label))
        return samples, hard

    def _build_from_risk_events(self, events: List[Dict[str, Any]]) -> Tuple[List[Tuple[str, str]], List[Tuple[str, str]]]:
        samples: List[Tuple[str, str]] = []
        hard: List[Tuple[str, str]] = []
        for row in events:
            text = " | ".join([
                self._safe_get(row, "type"),
                self._safe_get(row, "level"),
                self._safe_get(row, "status"),
                self._safe_get(row, "processLog", "process_log"),
            ])
            label = self._label_text(text)
            samples.append((text, label))
            hard.append((f"风险处置记录：{self._obfuscate(text)}", label))
        return samples, hard

    def _build_from_assets(self, assets: List[Dict[str, Any]]) -> Tuple[List[Tuple[str, str]], List[Tuple[str, str]]]:
        samples: List[Tuple[str, str]] = []
        hard: List[Tuple[str, str]] = []
        for row in assets:
            text = " | ".join([
                self._safe_get(row, "name"),
                self._safe_get(row, "type"),
                self._safe_get(row, "location"),
                self._safe_get(row, "description"),
                self._safe_get(row, "lineage"),
            ])
            label = self._label_text(text)
            if label == "unknown" and str(row.get("sensitivityLevel", "")).lower() in {"high", "critical", "p0"}:
                label = "address" if _RE_ADDRESS.search(text) else "unknown"
            samples.append((text, label))
            hard.append((f"资产变更轨迹::{self._obfuscate(text)}", label))
        return samples, hard

    def _build_from_adversarial_report(self, report_path: Path) -> Tuple[List[Tuple[str, str]], List[Tuple[str, str]]]:
        if not report_path.exists():
            return [], []
        try:
            data = json.loads(report_path.read_text(encoding="utf-8"))
        except Exception:
            return [], []
        rounds = data.get("rounds") if isinstance(data, dict) else []
        if not isinstance(rounds, list):
            rounds = []
        samples: List[Tuple[str, str]] = []
        hard: List[Tuple[str, str]] = []
        for row in rounds:
            if not isinstance(row, dict):
                continue
            narrative = self._safe_get(row, "narrative")
            attack = self._safe_get(row, "attack_strategy")
            defense = self._safe_get(row, "defense_strategy")
            text = f"adversarial::{attack}::{defense}::{narrative}".strip(":")
            label = self._label_text(narrative)
            samples.append((text, label))
            hard.extend([
                (f"{text} | prompt_injection_variant", label),
                (f"{self._obfuscate(narrative)} | stealth_mode", label),
            ])
        return samples, hard

    def _dedupe(self, pairs: List[Tuple[str, str]]) -> List[Tuple[str, str]]:
        seen = set()
        out: List[Tuple[str, str]] = []
        for text, label in pairs:
            t = (text or "").strip()
            l = label if label in LABELS else "unknown"
            key = (t, l)
            if not t or key in seen:
                continue
            seen.add(key)
            out.append((t, l))
        return out

    def _fetch_backend_data(self, backend_base_url: str, username: str, password: str, timeout: int = 20) -> Dict[str, Any]:
        base = backend_base_url.rstrip("/")
        auth = requests.post(
            f"{base}/api/auth/login",
            json={"username": username, "password": password},
            timeout=timeout,
        )
        auth.raise_for_status()
        login = auth.json()
        token = ((login or {}).get("data") or {}).get("token")
        if not token:
            raise RuntimeError("login token missing")
        headers = {"Authorization": f"Bearer {token}"}

        def get(path: str, params: Optional[Dict[str, Any]] = None) -> Any:
            resp = requests.get(f"{base}{path}", headers=headers, params=params or {}, timeout=timeout)
            resp.raise_for_status()
            body = resp.json()
            return body.get("data") if isinstance(body, dict) else body

        now = datetime.now()
        start = (now - timedelta(days=30)).strftime("%Y-%m-%d 00:00:00")
        end = now.strftime("%Y-%m-%d 23:59:59")
        audits = get("/api/audit-log/search", params={"from": start, "to": end})
        risks = get("/api/risk-event/list")
        assets = get("/api/data-asset/list")
        return {
            "auditLogs": audits if isinstance(audits, list) else [],
            "riskEvents": risks if isinstance(risks, list) else [],
            "dataAssets": assets if isinstance(assets, list) else [],
        }

    def build_dataset(
        self,
        backend_base_url: Optional[str] = None,
        username: Optional[str] = None,
        password: Optional[str] = None,
        fallback_files: Optional[Dict[str, str]] = None,
        include_adversarial: bool = True,
        max_samples: int = 5000,
    ) -> Dict[str, Any]:
        source_payload: Dict[str, Any] = {"auditLogs": [], "riskEvents": [], "dataAssets": []}
        source_mode = "fallback_files"

        if backend_base_url and username and password:
            try:
                source_payload = self._fetch_backend_data(backend_base_url, username, password)
                source_mode = "backend_api"
            except Exception:
                source_mode = "backend_api_failed_fallback"

        if source_mode != "backend_api":
            files = fallback_files or {}
            for key, file_name in {
                "auditLogs": files.get("auditLogs") or "audit_logs.json",
                "riskEvents": files.get("riskEvents") or "risk_events.json",
                "dataAssets": files.get("dataAssets") or "data_assets.json",
            }.items():
                p = self.base_dir / file_name
                if p.exists():
                    try:
                        payload = json.loads(p.read_text(encoding="utf-8"))
                        source_payload[key] = payload if isinstance(payload, list) else []
                    except Exception:
                        source_payload[key] = []

        samples: List[Tuple[str, str]] = []
        hard: List[Tuple[str, str]] = []

        s1, h1 = self._build_from_audit_logs(source_payload.get("auditLogs", []))
        s2, h2 = self._build_from_risk_events(source_payload.get("riskEvents", []))
        s3, h3 = self._build_from_assets(source_payload.get("dataAssets", []))
        samples.extend(s1 + s2 + s3)
        hard.extend(h1 + h2 + h3)

        if include_adversarial:
            adv_samples, adv_hard = self._build_from_adversarial_report(self.base_dir / "report.json")
            samples.extend(adv_samples)
            hard.extend(adv_hard)

        samples = self._dedupe(samples)[:max_samples]
        hard = self._dedupe(hard)[:max_samples]
        merged = self._dedupe(samples + hard)[:max_samples]

        label_counts: Dict[str, int] = {label: 0 for label in LABELS}
        for _, label in merged:
            label_counts[label] += 1

        ts = datetime.utcnow().strftime("%Y%m%d%H%M%S")
        output_file = self.generated_dir / f"training_data_factory_{ts}.json"
        latest_file = self.generated_dir / "training_data_factory_latest.json"

        payload = {
            "meta": {
                "builtAt": datetime.utcnow().isoformat() + "Z",
                "sourceMode": source_mode,
                "counts": {
                    "auditLogs": len(source_payload.get("auditLogs", [])),
                    "riskEvents": len(source_payload.get("riskEvents", [])),
                    "dataAssets": len(source_payload.get("dataAssets", [])),
                    "samples": len(samples),
                    "hardExamples": len(hard),
                    "merged": len(merged),
                },
                "labelCounts": label_counts,
            },
            "samples": [{"text": t, "label": l} for t, l in samples],
            "hard_examples": [{"text": t, "label": l} for t, l in hard],
            "merged_samples": [{"text": t, "label": l} for t, l in merged],
        }

        output_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        latest_file.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

        return {
            "outputFile": str(output_file),
            "latestFile": str(latest_file),
            "sourceMode": source_mode,
            "counts": payload["meta"]["counts"],
            "labelCounts": label_counts,
        }
