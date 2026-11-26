#!/usr/bin/env python3
"""Isolated, opt-in MySQL acceptance and restore drill. Never accepts an external DB URL."""
import argparse
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = "health_qa_acceptance"
RESTORE = "health_qa_restore"
MYSQL_READY_TIMEOUT = 120
MYSQL_COMMAND_TIMEOUT = 10


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def mysql_failure(error):
    """Publish only error codes, never SQL, credential values or raw container output."""
    detail = getattr(error, "stderr", b"") or b""
    if isinstance(detail, bytes):
        detail = detail.decode("utf-8", errors="replace")
    match = re.search(r"ERROR\s+(\d+)\s+\(([A-Z0-9]+)\)", detail)
    if match:
        return f"MySQL ERROR {match[1]}, SQLSTATE {match[2]}"
    if isinstance(error, subprocess.TimeoutExpired):
        return "hết thời gian chờ lệnh MySQL"
    return f"lệnh MySQL/Docker thoát với mã {error.returncode}"


def mysql(container, sql, database=None, operation="thực thi SQL QA"):
    args = ["docker", "exec", "-i", container, "sh", "-c",
            'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --batch --skip-column-names "$@"', "qa"]
    if database:
        args.append(database)
    try:
        return run(args, input=sql.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                   timeout=MYSQL_COMMAND_TIMEOUT).stdout
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        raise RuntimeError(f"Không hoàn tất bước {operation}: {mysql_failure(error)}.") from None


def wait_for_mysql(container, timeout=MYSQL_READY_TIMEOUT):
    # The official image starts a temporary socket-only server while creating users/DBs.
    # TCP + the application user/schema proves final initialization has completed.
    args = ["docker", "exec", "-i", container, "sh", "-c",
            'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=TCP --host=127.0.0.1 '
            '--connect-timeout=2 --user=health_qa --batch --skip-column-names health_qa_acceptance']
    deadline = time.monotonic() + timeout
    last_error = "chưa nhận được kết nối TCP"
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError(f"QA MySQL chưa sẵn sàng qua TCP/tài khoản QA sau {timeout} giây: {last_error}.")
        try:
            run(args, input=b"SELECT 1", stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                timeout=min(MYSQL_COMMAND_TIMEOUT, remaining))
            return
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            last_error = mysql_failure(error)
        time.sleep(min(1, max(0, deadline - time.monotonic())))


def digest(container, database):
    tables = mysql(container, f"SELECT table_name FROM information_schema.tables WHERE table_schema='{database}' ORDER BY table_name").decode().splitlines()
    result = {}
    for table in tables:
        if not table.replace('_', '').isalnum():
            raise RuntimeError("Unexpected QA table name")
        count = mysql(container, f"SELECT COUNT(*) FROM `{table}`", database).decode().strip()
        checksum = mysql(container, f"CHECKSUM TABLE `{table}` EXTENDED", database).decode().strip().split('\t')[-1]
        result[table] = {"rows": int(count), "checksum": checksum}
    return result


def restore_drill(container, folder):
    dump = folder / "synthetic-mysql.sql"
    with dump.open("wb") as output:
        dump.chmod(0o600)
        run(["docker", "exec", container, "sh", "-c",
             'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysqldump -uroot --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF health_qa_acceptance'], stdout=output, stderr=subprocess.PIPE)
    mysql(container, "CREATE DATABASE health_qa_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    with dump.open("rb") as data:
        run(["docker", "exec", "-i", container, "sh", "-c",
             'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot health_qa_restore'], stdin=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    original, restored = digest(container, SCHEMA), digest(container, RESTORE)
    if original != restored:
        raise RuntimeError("QA restore row counts/checksums differ")
    return {"tables": len(original), "rowCountsAndChecksumsEqual": True,
            "healthTables": {name: value["rows"] for name, value in original.items() if name.startswith("hr_health_")}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", choices=["mysql:8.0", "mysql:8.4"], default="mysql:8.0")
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--java-home")
    args = parser.parse_args()
    if not shutil.which("docker") or not shutil.which(args.maven):
        raise SystemExit("Cần Docker và Maven đã cài; script không tự tải dependencies/image.")
    folder = Path(tempfile.mkdtemp(prefix="cfc-health-qa-")); folder.chmod(0o700)
    password = secrets.token_urlsafe(24)
    env_file = folder / "mysql.env"
    env_file.write_text(f"MYSQL_ROOT_PASSWORD={secrets.token_urlsafe(24)}\nMYSQL_DATABASE={SCHEMA}\nMYSQL_USER=health_qa\nMYSQL_PASSWORD={password}\n")
    env_file.chmod(0o600)
    container = "cfc-health-qa-" + uuid.uuid4().hex[:12]
    started = False
    try:
        run(["docker", "run", "--pull=never", "-d", "--name", container, "--label", "cfc.health.qa=phase8",
             "--env-file", str(env_file), "-p", "127.0.0.1::3306", "--tmpfs", "/var/lib/mysql:rw,nosuid,size=768m",
             "--memory=1g", "--cpus=2", args.image], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        started = True
        wait_for_mysql(container)
        mysql(container, "CREATE DATABASE health_qa_upgrade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON health_qa_upgrade.* TO 'health_qa'@'%'",
              operation="tạo schema và cấp quyền QA nâng cấp")
        binding = run(["docker", "port", container, "3306/tcp"], capture_output=True, text=True).stdout.strip()
        if not binding.startswith("127.0.0.1:") or not binding.split(':')[-1].isdigit():
            raise RuntimeError("QA MySQL must bind loopback only")
        url = f"jdbc:mysql://{binding}/{SCHEMA}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        env = os.environ.copy(); env["HEALTH_MYSQL_QA_PASSWORD"] = password
        if args.java_home:
            env["JAVA_HOME"] = args.java_home
        log = folder / "maven.log"
        with log.open("w") as output:
            log.chmod(0o600)
            run([args.maven, "--offline", "-B", "-ntp", "-f", str(ROOT / "backend/pom.xml"),
                 f"-Dbooking.build.directory={folder / 'target'}", "-Dtest=HealthMysqlAcceptanceTest,Health2026MysqlReadinessTest,HealthHistoryMysqlImportTest,HealthLeadershipPayrollMysqlTest",
                 f"-Dhealth.mysql.qa.url={url}", "test"], cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        proof = restore_drill(container, folder)
        proof.update({"image": args.image, "mysqlVersion": mysql(container, "SELECT VERSION()").decode().strip(),
                      "isolated": True, "productionTouched": False})
        evidence = folder / "proof.json"
        evidence.write_text(json.dumps(proof, ensure_ascii=False, indent=2)); evidence.chmod(0o600)
        print(f"PASS MySQL API + migration + backup/restore. Bằng chứng private: {folder}")
    finally:
        if started:
            subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        env_file.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
