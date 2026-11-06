#!/usr/bin/env python3
"""Install the opt-in systemd user deployment timer from a verified release."""

import argparse
import getpass
import os
from pathlib import Path
import shutil
import subprocess
import sys


def quoted(value, unit=False):
    value = str(value)
    if "\n" in value or "\r" in value:
        raise RuntimeError("Đường dẫn hoặc môi trường chứa xuống dòng không hợp lệ.")
    value = value.replace("\\", "\\\\").replace('"', '\\"')
    return '"' + (value.replace("%", "%%") if unit else value) + '"'


def render_service(template, root, environment, python):
    # Directory/file directives consume the full path; ExecStart tokenizes arguments.
    for name, value in {"REPO_ROOT": root, "DEPLOY_ENV": environment}.items():
        quoted(value, unit=True)
        template = template.replace(f"@{name}@", str(value).replace("%", "%%"))
    for name, value in {"PYTHON": python, "DEPLOY_SCRIPT": root / "deployserver/linux/ci-deploy.py"}.items():
        template = template.replace(f"@{name}@", quoted(value, unit=True))
    return template


def install():
    if os.getuid() == 0:
        raise RuntimeError("Chạy dưới user david-nguyen, không dùng sudo cho installer.")
    for command in ("systemctl", "loginctl", "git", "node", "npm", "java", "docker", "curl", "cloudflared"):
        if not shutil.which(command):
            raise RuntimeError(f"Không tìm thấy {command} trong PATH hiện tại.")
    linger = subprocess.check_output(["loginctl", "show-user", str(os.getuid()), "--property=Linger", "--value"], text=True).strip()
    if linger != "yes":
        raise RuntimeError(f"Cần bật chạy sau khi logout/reboot trước: sudo loginctl enable-linger {getpass.getuser()}")
    root = Path(__file__).resolve().parents[2]
    if subprocess.check_output(["git", "-C", str(root), "status", "--porcelain"], text=True).strip():
        raise RuntimeError("Checkout chưa sạch; không cài timer.")
    subprocess.run([sys.executable, str(root / "deployserver/linux/ci-deploy.py"), "--status"], check=True)
    config = Path(os.environ.get("XDG_CONFIG_HOME", str(Path.home() / ".config")))
    units = config / "systemd/user"
    environment = config / "cfcbase/deploy.env"
    units.mkdir(parents=True, exist_ok=True)
    environment.parent.mkdir(parents=True, exist_ok=True)
    # Only tool paths are persisted; production secrets stay in the existing .env.
    values = {"PATH": os.environ["PATH"]}
    if os.environ.get("JAVA_HOME"):
        values["JAVA_HOME"] = os.environ["JAVA_HOME"]
    environment.write_text("".join(f"{key}={quoted(value)}\n" for key, value in values.items()))
    environment.chmod(0o600)
    source = root / "deployserver/linux/systemd"
    service = render_service((source / "bookingbase-deploy.service").read_text(), root, environment, sys.executable)
    for name, content in (("bookingbase-deploy.service", service),
                          ("bookingbase-deploy.timer", (source / "bookingbase-deploy.timer").read_text())):
        target = units / name
        target.write_text(content)
        target.chmod(0o600)
    subprocess.run(["systemctl", "--user", "daemon-reload"], check=True)
    subprocess.run(["systemctl", "--user", "enable", "--now", "bookingbase-deploy.timer"], check=True)
    subprocess.run(["systemctl", "--user", "restart", "bookingbase-deploy.timer"], check=True)
    subprocess.run(["systemctl", "--user", "list-timers", "bookingbase-deploy.timer", "--no-pager"], check=True)
    print("Đã bật auto-deploy sau CI. Xem log: journalctl --user -u bookingbase-deploy.service -n 80 --no-pager")


if __name__ == "__main__":
    argparse.ArgumentParser(description="Cài và bật systemd user timer deploy; cần deploy thủ công thành công trước.").parse_args()
    try:
        install()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"[CFCBase Deploy Timer] Không cài đặt thành công: {error}", file=sys.stderr)
        sys.exit(1)
