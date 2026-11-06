"""Persistent deployment state, protected by ci-deploy.py's repository lock."""

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re

STATE_FILE = "cfcbase-deploy-state.json"
MIGRATION_PATHS = ("backend/src/main/resources/db/migration", "backend/src/main/java/db/migration")


def load_state(path):
    if not path.exists():
        return {}
    state = json.loads(path.read_text())
    if not isinstance(state, dict) or state.get("phase") not in {"success", "deploying", "failed"}:
        raise RuntimeError("Trạng thái deploy không hợp lệ; không tự ghi đè hoặc deploy.")
    for field in ("attempt_sha", "last_success_sha"):
        value = state.get(field)
        if value is not None and (not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{40,64}", value)):
            raise RuntimeError("SHA trong trạng thái deploy không hợp lệ.")
    return state


def save_state(path, state, phase, sha):
    updated = {**state, "phase": phase, "attempt_sha": sha,
               "updated_at": datetime.now(timezone.utc).isoformat()}
    if phase == "success":
        updated["last_success_sha"] = sha
    temporary = path.with_suffix(".tmp")
    with temporary.open("w") as handle:
        os.chmod(temporary, 0o600)
        json.dump(updated, handle, indent=2)
        handle.flush()
        os.fsync(handle.fileno())
    temporary.replace(path)
    return updated


def require_ready(state, root):
    if state.get("phase") != "success" or not state.get("last_success_sha"):
        raise RuntimeError("Auto-deploy chưa sẵn sàng hoặc đã tạm dừng. Kiểm tra log; deploy thủ công thành công trước. "
                           "Nếu lần trước lỗi/gián đoạn, cần --deploy --retry-failed sau khi xử lý nguyên nhân.")
    release = Path(root) / "backend/target/release-version.txt"
    version = release.read_text().splitlines()[0] if release.is_file() and release.stat().st_size else ""
    if version != state["last_success_sha"]:
        raise RuntimeError("Release hiện tại không khớp lần deploy thành công; kiểm tra rồi deploy thủ công để đồng bộ.")


def require_retry_acknowledgement(state, retry_failed):
    if state and state.get("phase") != "success" and not retry_failed:
        raise RuntimeError("Lần deploy trước lỗi/gián đoạn. Xử lý nguyên nhân trước, sau đó dùng --deploy --retry-failed.")
