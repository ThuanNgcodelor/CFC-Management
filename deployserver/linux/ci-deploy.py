#!/usr/bin/env python3
"""Manual, outbound-only deployment of a public main commit with successful CI."""

import argparse
import fcntl
import json
from pathlib import Path
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPOSITORY = "ThuanNgcodelor/CFC-Management"
REQUIRED_JOBS = {"Frontend lint and build", "Backend tests"}
API_ROOT = f"https://api.github.com/repos/{REPOSITORY}"
API_TIMEOUT_SECONDS = 30


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
        raise RuntimeError("Chưa có CI push trên main cho commit này. Chờ CI rồi kiểm tra lại.")
    run = max(matches, key=lambda value: (value["run_number"], value.get("run_attempt", 1)))
    if run.get("status") != "completed" or run.get("conclusion") != "success":
        raise RuntimeError("CI mới nhất của commit chưa thành công; không cập nhật release.")
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


def prepare(root):
    if git(root, "branch", "--show-current") != "main":
        raise RuntimeError("Checkout Fedora phải ở nhánh main; script không tự chuyển nhánh.")
    if git(root, "status", "--porcelain"):
        raise RuntimeError("Checkout có thay đổi chưa lưu; xử lý trước, script không reset/stash code.")
    subprocess.run(["git", "-C", str(root), "fetch", "--no-tags", "origin", "refs/heads/main"], check=True)
    candidate = git(root, "rev-parse", "FETCH_HEAD")
    previous = git(root, "rev-parse", "HEAD")
    subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", previous, candidate], check=True)
    run = verify_ci(candidate)
    print(f"CI đạt: {run['html_url']}", flush=True)
    print(f"Commit hiện tại: {previous}\nCommit sẽ deploy: {candidate}", flush=True)
    return previous, candidate


def main():
    parser = argparse.ArgumentParser(description="Kiểm tra CI và deploy thủ công trên Fedora; không bật tự deploy.")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true", help="Chỉ fetch và kiểm tra; không đổi working tree/release.")
    mode.add_argument("--deploy", action="store_true", help="Sau khi CI đạt, fast-forward main rồi build/deploy.")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    lock_path = Path(git(root, "rev-parse", "--absolute-git-dir")) / "cfcbase-deploy.lock"
    with lock_path.open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError("Một lệnh kiểm tra/deploy khác đang chạy.") from error
        previous, candidate = prepare(root)
        if not args.deploy:
            print("Chỉ kiểm tra: chưa cập nhật code hoặc khởi động lại hệ thống.")
            return
        runner = subprocess.run(["systemctl", "--user", "is-active", "--quiet", "cfcbase-runner.service"], check=False)
        if runner.returncode == 0:
            raise RuntimeError("Dừng cfcbase-runner.service và gỡ runner khỏi repo public trước khi deploy bằng cơ chế pull.")
        if runner.returncode not in (3, 4):
            raise RuntimeError("Không kiểm tra được user service runner; kiểm tra systemctl --user trước.")
        if git(root, "rev-parse", "HEAD") != previous or git(root, "status", "--porcelain"):
            raise RuntimeError("Checkout đã thay đổi trong lúc kiểm tra; chạy lại.")
        subprocess.run(["git", "-C", str(root), "merge", "--ff-only", candidate], check=True)
        print("Bắt đầu build/deploy. Health-check và rollback JAR do script hiện có xử lý.", flush=True)
        subprocess.run(["bash", str(root / "deployserver/linux/build-prod.sh")], cwd=root, check=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, HTTPError, URLError, OSError, ValueError, KeyError) as error:
        print(f"[CFCBase Deploy] Không hoàn tất: {error}", file=sys.stderr)
        sys.exit(1)
