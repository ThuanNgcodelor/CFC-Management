#!/usr/bin/env python3
"""Outbound-only manual or timer deployment of a public main commit after CI."""

import argparse
import fcntl
import json
from pathlib import Path
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from ci_deploy_state import (
    STATE_FILE, load_state, require_ready,
    require_retry_acknowledgement, save_state,
)

from ci_deploy_migrations import changed_migrations, require_auto_migrations

REPOSITORY = "ThuanNgcodelor/CFC-Management"
REQUIRED_JOBS = {"Frontend lint and build", "Backend tests", "Deployment script checks"}
API_ROOT = f"https://api.github.com/repos/{REPOSITORY}"
PUBLIC_GIT_URL = f"https://github.com/{REPOSITORY}.git"
API_TIMEOUT_SECONDS = 30


class CiNotReady(RuntimeError):
    """CI has not approved this commit yet; no release attempt was made."""


def api_get(path):
    request = Request(API_ROOT + path, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "CFCBase-manual-deploy",
    })
    with urlopen(request, timeout=API_TIMEOUT_SECONDS) as response:
        return json.load(response)


def latest_run(runs, sha):
    matches = [run for run in runs if run.get("head_sha") == sha
               and run.get("head_branch") == "main" and run.get("event") == "push"]
    if not matches:
        raise CiNotReady("Chưa có CI push trên main cho commit này. Chờ CI rồi kiểm tra lại.")
    run = max(matches, key=lambda value: (value["run_number"], value.get("run_attempt", 1)))
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        raise CiNotReady("CI mới nhất của commit chưa thành công; không cập nhật release.")
    return run


def verify_jobs(jobs):
    for name in REQUIRED_JOBS:
        matches = [job for job in jobs if job.get("name") == name]
        if len(matches) != 1 or matches[0].get("status") != "completed" or matches[0].get("conclusion") != "success":
            raise RuntimeError(f"Job bắt buộc chưa đạt: {name}.")


def verify_ci(sha):
    query = urlencode({"head_sha": sha, "branch": "main", "event": "push", "per_page": 100})
    data = api_get(f"/actions/workflows/ci.yml/runs?{query}")
    run = latest_run(data.get("workflow_runs", []), sha)
    jobs = api_get(f"/actions/runs/{run['id']}/attempts/{run.get('run_attempt', 1)}/jobs?per_page=100")
    if jobs.get("total_count", 0) > 100:
        raise RuntimeError("CI có quá nhiều job; cần rà soát cấu hình deploy trước.")
    verify_jobs(jobs.get("jobs", []))
    return run


def git(root, *arguments):
    return subprocess.check_output(["git", "-C", str(root), *arguments], text=True).strip()


def prepare(root, check_ci=True, remote="origin"):
    if git(root, "branch", "--show-current") != "main":
        raise RuntimeError("Checkout Fedora phải ở nhánh main; script không tự chuyển nhánh.")
    if git(root, "status", "--porcelain"):
        raise RuntimeError("Checkout có thay đổi chưa lưu; xử lý trước, script không reset/stash code.")
    subprocess.run(["git", "-C", str(root), "fetch", "--no-tags", remote, "refs/heads/main"], check=True)
    candidate = git(root, "rev-parse", "FETCH_HEAD")
    previous = git(root, "rev-parse", "HEAD")
    subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", previous, candidate], check=True)
    if check_ci:
        run = verify_ci(candidate)
        print(f"CI đạt: {run['html_url']}", flush=True)
    print(f"Commit hiện tại: {previous}\nCommit sẽ deploy: {candidate}", flush=True)
    return previous, candidate


def require_services(check_running=True):
    runner = subprocess.run(["systemctl", "--user", "is-active", "--quiet", "cfcbase-runner.service"], check=False)
    if runner.returncode == 0:
        raise RuntimeError("Dừng cfcbase-runner.service và gỡ runner khỏi repo public trước khi deploy bằng cơ chế pull.")
    if runner.returncode not in (3, 4):
        raise RuntimeError("Không kiểm tra được user service runner; kiểm tra systemctl --user trước.")
    if check_running:
        for unit in ("bookingbase-backend.service", "bookingbase-tunnel.service"):
            subprocess.run(["systemctl", "--user", "is-active", "--quiet", unit], check=True)


def deploy_release(root, previous, candidate, state_path, state, backup_required=False):
    if git(root, "rev-parse", "HEAD") != previous or git(root, "status", "--porcelain"):
        raise RuntimeError("Checkout đã thay đổi trong lúc kiểm tra; chạy lại.")
    # Persist before any mutation so power loss/timeout cannot silently retry migrations.
    state = save_state(state_path, state, "deploying", candidate)
    try:
        subprocess.run(["git", "-C", str(root), "merge", "--ff-only", candidate], check=True)
        print("Bắt đầu build/deploy. Health-check và rollback JAR do script hiện có xử lý.", flush=True)
        command = ["bash", str(root / "deployserver/linux/build-prod.sh")]
        if backup_required:
            command.append("--backup-before-activate")
            print("Có migration: build/smoke trước, backup database đạt mới kích hoạt; database không tự rollback.", flush=True)
        subprocess.run(command, cwd=root, check=True)
        require_services()
        require_ready({"phase": "success", "last_success_sha": candidate}, root)
    except (Exception, KeyboardInterrupt):
        save_state(state_path, state, "failed", candidate)
        raise
    save_state(state_path, state, "success", candidate)
    print(f"Deploy thành công: {candidate}", flush=True)


def auto_deploy(root, state_path, state):
    require_ready(state, root)
    require_services()
    # A user timer cannot depend on an interactive SSH agent after logout/reboot.
    previous, candidate = prepare(root, check_ci=False, remote=PUBLIC_GIT_URL)
    if candidate == state["last_success_sha"]:
        print("Đang chạy bản mới nhất; không build/restart lại.")
        return
    try:
        run = verify_ci(candidate)
    except CiNotReady as error:
        print(str(error))
        return
    print(f"CI đạt: {run['html_url']}", flush=True)
    backup_required = require_auto_migrations(root, state["last_success_sha"], candidate)
    deploy_release(root, previous, candidate, state_path, state, backup_required=backup_required)


def main():
    parser = argparse.ArgumentParser(description="Kiểm tra CI và deploy trên Fedora; timer chỉ bật qua installer riêng.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="Chỉ fetch và kiểm tra; không đổi working tree/release.")
    mode.add_argument("--deploy", action="store_true", help="Sau khi CI đạt, fast-forward main rồi build/deploy.")
    mode.add_argument("--auto", action="store_true", help="Dành cho timer: CI đạt, backup trước migration tương thích, chặn lần deploy lỗi.")
    mode.add_argument("--status", action="store_true", help="Kiểm tra trạng thái thành công, release và dịch vụ trước khi bật timer.")
    parser.add_argument("--retry-failed", action="store_true", help="Chỉ cùng --deploy: xác nhận đã xử lý lần deploy lỗi/gián đoạn.")
    args = parser.parse_args()
    if args.retry_failed and not args.deploy:
        parser.error("--retry-failed chỉ dùng cùng --deploy.")
    root = Path(__file__).resolve().parents[2]
    lock_path = Path(git(root, "rev-parse", "--absolute-git-dir")) / "cfcbase-deploy.lock"
    with lock_path.open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("Một lệnh kiểm tra/deploy khác đang chạy.") from error
        state_path = lock_path.parent / STATE_FILE
        state = load_state(state_path)
        if args.status:
            if git(root, "branch", "--show-current") != "main" or git(root, "status", "--porcelain"):
                raise RuntimeError("Cần checkout main sạch trước khi bật auto-deploy.")
            print(f"Trạng thái deploy: {state.get('phase', 'chưa khởi tạo')}; commit thử gần nhất: {state.get('attempt_sha', 'chưa có')}")
            require_ready(state, root)
            require_services()
            print(f"Sẵn sàng tự deploy; release thành công gần nhất: {state['last_success_sha']}")
            return
        if args.auto:
            auto_deploy(root, state_path, state)
            return
        if args.deploy:
            require_retry_acknowledgement(state, args.retry_failed)
        previous, candidate = prepare(root)
        if args.check:
            print("Chỉ kiểm tra: chưa cập nhật code hoặc khởi động lại hệ thống.")
            return
        require_services(check_running=False)
        baseline = state.get("last_success_sha")
        backup_required = not baseline or bool(changed_migrations(root, baseline, candidate))
        deploy_release(root, previous, candidate, state_path, state, backup_required=backup_required)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, HTTPError, URLError, OSError, ValueError, KeyError) as error:
        print(f"[CFCBase Deploy] Không hoàn tất: {error}", file=sys.stderr)
        sys.exit(1)
