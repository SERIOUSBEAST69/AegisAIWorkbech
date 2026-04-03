import hashlib
import json
import os
import platform
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


MODEL_DIR = Path(os.environ.get("MODEL_DIR", "./models"))
RUNS_DIR = MODEL_DIR / "model_runs"
REGISTRY_FILE = MODEL_DIR / "model_registry.json"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _safe_jsonable(value: Any) -> Any:
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    if isinstance(value, dict):
        return {str(k): _safe_jsonable(v) for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [_safe_jsonable(v) for v in value]
    return str(value)


def _sha256_file(path: Path) -> Optional[str]:
    if not path.exists() or not path.is_file():
        return None
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text: str) -> str:
    digest = hashlib.sha256()
    digest.update((text or "").encode("utf-8"))
    return digest.hexdigest()


def build_data_version(paths: List[str], extra: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    normalized = []
    for raw in paths or []:
        path = Path(raw)
        if not path.is_absolute():
            path = (Path.cwd() / path).resolve()
        normalized.append(
            {
                "path": str(path),
                "sha256": _sha256_file(path),
                "sizeBytes": path.stat().st_size if path.exists() and path.is_file() else None,
            }
        )
    payload = {
        "paths": normalized,
        "extra": _safe_jsonable(extra or {}),
    }
    version_hash = sha256_text(json.dumps(payload, ensure_ascii=False, sort_keys=True))
    return {
        "versionHash": version_hash,
        "sources": normalized,
        "extra": payload["extra"],
    }


def build_code_version(signature_items: Dict[str, str]) -> Dict[str, Any]:
    normalized = _safe_jsonable(signature_items or {})
    version_hash = sha256_text(json.dumps(normalized, ensure_ascii=False, sort_keys=True))
    return {
        "versionHash": version_hash,
        "signature": normalized,
    }


def _read_registry() -> Dict[str, Any]:
    if not REGISTRY_FILE.exists():
        return {
            "schemaVersion": "1.0",
            "updatedAt": _utc_now_iso(),
            "totalRuns": 0,
            "latestByModel": {},
            "runs": [],
        }
    try:
        with open(REGISTRY_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            raise ValueError("registry root is not an object")
        data.setdefault("schemaVersion", "1.0")
        data.setdefault("updatedAt", _utc_now_iso())
        data.setdefault("totalRuns", 0)
        data.setdefault("latestByModel", {})
        data.setdefault("runs", [])
        return data
    except Exception:
        return {
            "schemaVersion": "1.0",
            "updatedAt": _utc_now_iso(),
            "totalRuns": 0,
            "latestByModel": {},
            "runs": [],
        }


def record_model_run(
    model_key: str,
    run_source: str,
    metrics: Optional[Dict[str, Any]] = None,
    data_summary: Optional[Dict[str, Any]] = None,
    hyper_params: Optional[Dict[str, Any]] = None,
    artifact_paths: Optional[List[str]] = None,
    notes: Optional[str] = None,
    data_version: Optional[Dict[str, Any]] = None,
    code_version: Optional[Dict[str, Any]] = None,
    evaluation_gate: Optional[Dict[str, Any]] = None,
    tags: Optional[List[str]] = None,
) -> Dict[str, Any]:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    RUNS_DIR.mkdir(parents=True, exist_ok=True)

    run_id = f"{model_key}_{datetime.utcnow().strftime('%Y%m%d%H%M%S%f')}"
    artifacts: List[Dict[str, Any]] = []
    for raw in artifact_paths or []:
        path = Path(raw)
        if not path.is_absolute():
            path = (Path.cwd() / path).resolve()
        artifacts.append(
            {
                "path": str(path),
                "exists": path.exists(),
                "sizeBytes": path.stat().st_size if path.exists() and path.is_file() else None,
                "sha256": _sha256_file(path),
            }
        )

    entry: Dict[str, Any] = {
        "runId": run_id,
        "timestamp": _utc_now_iso(),
        "modelKey": model_key,
        "source": run_source,
        "metrics": _safe_jsonable(metrics or {}),
        "dataSummary": _safe_jsonable(data_summary or {}),
        "hyperParams": _safe_jsonable(hyper_params or {}),
        "dataVersion": _safe_jsonable(data_version or {}),
        "codeVersion": _safe_jsonable(code_version or {}),
        "evaluationGate": _safe_jsonable(evaluation_gate or {}),
        "artifacts": artifacts,
        "notes": notes or "",
        "tags": _safe_jsonable(tags or []),
        "runtime": {
            "python": sys.version.split()[0],
            "platform": platform.platform(),
        },
    }

    run_file = RUNS_DIR / f"{run_id}.json"
    with open(run_file, "w", encoding="utf-8") as f:
        json.dump(entry, f, ensure_ascii=False, indent=2)

    registry = _read_registry()
    registry_runs: List[Dict[str, Any]] = list(registry.get("runs") or [])
    registry_runs.insert(
        0,
        {
            "runId": entry["runId"],
            "timestamp": entry["timestamp"],
            "modelKey": entry["modelKey"],
            "source": entry["source"],
            "runFile": str(run_file),
        },
    )
    registry["runs"] = registry_runs[:200]
    latest = dict(registry.get("latestByModel") or {})
    latest[model_key] = {
        "runId": entry["runId"],
        "timestamp": entry["timestamp"],
        "source": entry["source"],
        "runFile": str(run_file),
        "metrics": entry["metrics"],
        "dataVersion": entry["dataVersion"],
        "codeVersion": entry["codeVersion"],
        "evaluationGate": entry["evaluationGate"],
        "artifacts": entry["artifacts"],
        "tags": entry["tags"],
    }
    registry["latestByModel"] = latest
    registry["totalRuns"] = int(registry.get("totalRuns") or 0) + 1
    registry["updatedAt"] = _utc_now_iso()

    with open(REGISTRY_FILE, "w", encoding="utf-8") as f:
        json.dump(registry, f, ensure_ascii=False, indent=2)

    return {
        "runId": entry["runId"],
        "modelKey": entry["modelKey"],
        "timestamp": entry["timestamp"],
        "runFile": str(run_file),
        "registryFile": str(REGISTRY_FILE),
    }


def load_model_registry_summary() -> Dict[str, Any]:
    registry = _read_registry()
    latest = registry.get("latestByModel") or {}
    runs = registry.get("runs") or []
    return {
        "schemaVersion": registry.get("schemaVersion", "1.0"),
        "updatedAt": registry.get("updatedAt"),
        "totalRuns": int(registry.get("totalRuns") or 0),
        "trackedModelCount": len(latest),
        "latestByModel": latest,
        "recentRuns": runs[:20],
        "registryFile": str(REGISTRY_FILE),
        "runsDir": str(RUNS_DIR),
    }
