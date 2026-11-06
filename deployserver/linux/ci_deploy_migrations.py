"""Review declarations for additive migrations; never infer rollback safety from SQL keywords."""

import hashlib
import json
from pathlib import PurePosixPath
import re
import subprocess
from ci_deploy_state import MIGRATION_PATHS

MANIFEST = "deployserver/linux/auto-migrations.json"
SQL_MIGRATION = re.compile(r"backend/src/main/resources/db/migration/V([0-9]+)__[^/]+\.sql")


def git_output(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)


def changed_migrations(root, baseline, candidate):
    raw = git_output(root, "diff", "--name-status", "--no-renames", "-z", baseline, candidate, "--", *MIGRATION_PATHS)
    fields = raw.decode("utf-8").split("\0")
    return list(zip(fields[0:-1:2], fields[1:-1:2]))


def require_auto_migrations(root, baseline, candidate):
    changes = changed_migrations(root, baseline, candidate)
    if not changes:
        return False
    try:
        manifest = json.loads(git_output(root, "show", f"{candidate}:{MANIFEST}"))
        if manifest.get("version") != 1 or not isinstance(manifest.get("migrations"), dict):
            raise ValueError()
    except (subprocess.CalledProcessError, ValueError, AttributeError) as error:
        raise RuntimeError("Migration mới chưa có khai báo auto-deploy hợp lệ trong auto-migrations.json; chưa đổi release.") from error
    paths = git_output(root, "ls-tree", "-r", "--name-only", baseline, "--", *MIGRATION_PATHS).decode().splitlines()
    versions = [int(match.group(1)) for path in paths
                if (match := re.search(r"/V([0-9]+)__", path))]
    last_version = max(versions, default=0)
    added_versions = set()
    for action, path in changes:
        match = SQL_MIGRATION.fullmatch(path)
        if action != "A" or not match:
            raise RuntimeError("Auto-deploy chỉ nhận migration SQL mới đã duyệt; sửa/xóa migration cũ hoặc migration Java cần triển khai thủ công.")
        version = int(match.group(1))
        if version <= last_version or version in added_versions:
            raise RuntimeError("Migration auto phải có version mới, không trùng và lớn hơn release đang phục vụ.")
        added_versions.add(version)
        entry = manifest["migrations"].get(path, {})
        digest = hashlib.sha256(git_output(root, "show", f"{candidate}:{path}")).hexdigest()
        if (not isinstance(entry, dict) or entry.get("backwardCompatible") is not True
                or entry.get("sha256") != digest or not isinstance(entry.get("reason"), str)
                or not entry["reason"].strip()):
            raise RuntimeError(f"Migration {PurePosixPath(path).name} chưa được khai báo tương thích ngược đúng nội dung SHA256; chưa đổi release.")
    return True
