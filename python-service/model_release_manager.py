import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional


MODEL_DIR = Path(os.environ.get("MODEL_DIR", "./models"))
RELEASE_FILE = MODEL_DIR / "model_release_registry.json"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _read_registry() -> Dict[str, Any]:
    if not RELEASE_FILE.exists():
        return {
            "schemaVersion": "1.0",
            "updatedAt": _utc_now_iso(),
            "stable": None,
            "canary": None,
            "candidates": [],
            "history": [],
        }
    try:
        data = json.loads(RELEASE_FILE.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError("invalid registry")
        data.setdefault("schemaVersion", "1.0")
        data.setdefault("updatedAt", _utc_now_iso())
        data.setdefault("stable", None)
        data.setdefault("canary", None)
        data.setdefault("candidates", [])
        data.setdefault("history", [])
        return data
    except Exception:
        return {
            "schemaVersion": "1.0",
            "updatedAt": _utc_now_iso(),
            "stable": None,
            "canary": None,
            "candidates": [],
            "history": [],
        }


def _write_registry(registry: Dict[str, Any]) -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    registry["updatedAt"] = _utc_now_iso()
    RELEASE_FILE.write_text(json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8")


def _evaluate_gate(metrics: Dict[str, Any], gate: Dict[str, Any]) -> Dict[str, Any]:
    required_macro_f1 = float(gate.get("macroF1Min", 0.88))
    required_test_acc = float(gate.get("testAccuracyMin", 0.90))
    macro_f1 = float(metrics.get("macro_f1", 0.0))
    test_acc = float(metrics.get("test_accuracy", 0.0))
    passed = macro_f1 >= required_macro_f1 and test_acc >= required_test_acc
    return {
        "passed": passed,
        "macroF1": macro_f1,
        "testAccuracy": test_acc,
        "required": {
            "macroF1Min": required_macro_f1,
            "testAccuracyMin": required_test_acc,
        },
    }


def register_candidate(model_key: str, run_id: str, metrics: Dict[str, Any], gate: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    gate_result = _evaluate_gate(metrics or {}, gate or {})
    registry = _read_registry()
    candidate = {
        "candidateId": f"cand_{run_id}",
        "modelKey": model_key,
        "runId": run_id,
        "createdAt": _utc_now_iso(),
        "metrics": metrics or {},
        "gate": gate_result,
        "status": "ready" if gate_result.get("passed") else "rejected",
    }
    registry["candidates"] = [candidate] + [c for c in registry.get("candidates", []) if c.get("candidateId") != candidate["candidateId"]]
    registry["history"] = [
        {
            "action": "register_candidate",
            "candidateId": candidate["candidateId"],
            "status": candidate["status"],
            "timestamp": _utc_now_iso(),
        }
    ] + list(registry.get("history", []))[:300]
    _write_registry(registry)
    return candidate


def promote_canary(candidate_id: str, traffic_percent: float = 10.0) -> Dict[str, Any]:
    registry = _read_registry()
    candidates = registry.get("candidates", [])
    target = next((c for c in candidates if c.get("candidateId") == candidate_id), None)
    if not target:
        raise ValueError("candidate not found")
    if not target.get("gate", {}).get("passed"):
        raise ValueError("candidate gate not passed")

    registry["canary"] = {
        "candidateId": candidate_id,
        "runId": target.get("runId"),
        "modelKey": target.get("modelKey"),
        "trafficPercent": max(1.0, min(100.0, float(traffic_percent))),
        "promotedAt": _utc_now_iso(),
    }
    registry["history"] = [
        {
            "action": "promote_canary",
            "candidateId": candidate_id,
            "trafficPercent": registry["canary"]["trafficPercent"],
            "timestamp": _utc_now_iso(),
        }
    ] + list(registry.get("history", []))[:300]
    _write_registry(registry)
    return registry["canary"]


def promote_stable(candidate_id: str) -> Dict[str, Any]:
    registry = _read_registry()
    candidates = registry.get("candidates", [])
    target = next((c for c in candidates if c.get("candidateId") == candidate_id), None)
    if not target:
        raise ValueError("candidate not found")
    if not target.get("gate", {}).get("passed"):
        raise ValueError("candidate gate not passed")

    previous_stable = registry.get("stable")
    registry["stable"] = {
        "candidateId": candidate_id,
        "runId": target.get("runId"),
        "modelKey": target.get("modelKey"),
        "promotedAt": _utc_now_iso(),
    }
    registry["canary"] = None
    registry["history"] = [
        {
            "action": "promote_stable",
            "candidateId": candidate_id,
            "previousStable": previous_stable,
            "timestamp": _utc_now_iso(),
        }
    ] + list(registry.get("history", []))[:300]
    _write_registry(registry)
    return registry["stable"]


def rollback_to(run_id: str) -> Dict[str, Any]:
    registry = _read_registry()
    candidates = registry.get("candidates", [])
    target = next((c for c in candidates if c.get("runId") == run_id), None)
    if not target:
        raise ValueError("target runId not found in candidates")

    previous_stable = registry.get("stable")
    registry["stable"] = {
        "candidateId": target.get("candidateId"),
        "runId": target.get("runId"),
        "modelKey": target.get("modelKey"),
        "promotedAt": _utc_now_iso(),
        "rollback": True,
    }
    registry["canary"] = None
    registry["history"] = [
        {
            "action": "rollback",
            "targetRunId": run_id,
            "previousStable": previous_stable,
            "timestamp": _utc_now_iso(),
        }
    ] + list(registry.get("history", []))[:300]
    _write_registry(registry)
    return registry["stable"]


def release_status() -> Dict[str, Any]:
    return _read_registry()
