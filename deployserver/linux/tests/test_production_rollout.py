"""Execute production shell scripts with fake build/tools; never start real services."""

import gzip
import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / "deployserver/linux"


class ShellFixture(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)
        self.root = self.base / "repo"
        self.scripts = self.root / "deployserver/linux"
        self.scripts.mkdir(parents=True)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.events = self.base / "events"
        self.environment = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
                            "TEST_EVENTS": str(self.events), "XDG_RUNTIME_DIR": str(self.base),
                            "BOOKINGBASE_BACKUP_DIR": str(self.base / "backups")}

    def executable(self, path, source):
        path.write_text("#!/usr/bin/env bash\nset -eu\n" + source)
        path.chmod(0o755)

    def tool(self, name, source):
        self.executable(self.bin / name, source)

    def execute(self, script, *args, **env):
        return subprocess.run(["bash", str(script), *args], cwd=self.root,
                              env={**self.environment, **env}, capture_output=True, text=True, timeout=15)


class BuildRolloutTest(ShellFixture):
    def setUp(self):
        super().setUp()
        shutil.copy(SCRIPTS / "build-prod.sh", self.scripts / "build-prod.sh")
        frontend = self.root / "frontend"
        frontend.mkdir()
        (frontend / "package-lock.json").write_text("{}")
        self.target = self.root / "backend/target"
        self.target.mkdir(parents=True)
        self.jar = self.target / "booking-system-0.0.1-SNAPSHOT.jar"
        self.marker = self.target / "release-version.txt"
        self.jar.write_text("old JAR")
        self.marker.write_text("old SHA\n")
        (self.root / ".gitignore").write_text("backend/target/\nbackend/target-build/\n")
        self.tool("node", "exit 0\n")
        self.tool("java", '''if [[ "${1:-}" == -version ]]; then echo 'openjdk version "21.0.1"' >&2; exit 0; fi
printf 'smoke\\n' >> "$TEST_EVENTS"
exit "${TEST_SMOKE_EXIT:-0}"
''')
        self.tool("npm", '''if [[ "$1" == run ]]; then printf 'frontend\\n' >> "$TEST_EVENTS"; exit "${TEST_BUILD_EXIT:-0}"; fi
''')
        self.executable(self.root / "backend/mvnw", '''printf 'backend-build\\n' >> "$TEST_EVENTS"
for arg in "$@"; do
 if [[ "$arg" == -Dbooking.build.directory=* ]]; then
  staging="${arg#*=}"; mkdir -p "$staging"; printf 'new JAR' > "$staging/booking-system-0.0.1-SNAPSHOT.jar"
 fi
done
''')
        self.executable(self.scripts / "backup-database.sh", '''printf 'backup\\n' >> "$TEST_EVENTS"
[[ "$(cat "$TEST_OLD_JAR")" == 'old JAR' ]]
exit "${TEST_BACKUP_EXIT:-0}"
''')
        self.executable(self.scripts / "run.sh", '''printf 'activate\\n' >> "$TEST_EVENTS"
exit "${TEST_ACTIVATE_EXIT:-0}"
''')
        self.environment["TEST_OLD_JAR"] = str(self.jar)
        for args in (("init", "--initial-branch=main"), ("add", "."),
                     ("-c", "user.name=Test", "-c", "user.email=test@example.test", "commit", "-m", "fixture")):
            subprocess.run(["git", "-C", str(self.root), *args], check=True, capture_output=True)

    def test_backup_runs_after_smoke_and_before_activation(self):
        result = self.execute(self.scripts / "build-prod.sh", "--backup-before-activate")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["frontend", "backend-build", "smoke", "backup", "activate"], self.events.read_text().splitlines())
        self.assertEqual("new JAR", self.jar.read_text())
        self.assertEqual("old JAR", Path(str(self.jar) + ".previous").read_text())

    def test_build_or_smoke_failure_preserves_old_release_without_backup(self):
        for variable in ("TEST_BUILD_EXIT", "TEST_SMOKE_EXIT"):
            with self.subTest(variable=variable):
                self.events.unlink(missing_ok=True)
                result = self.execute(self.scripts / "build-prod.sh", "--backup-before-activate", **{variable: "1"})
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("backup", self.events.read_text())
                self.assertNotIn("activate", self.events.read_text())
                self.assertEqual("old JAR", self.jar.read_text())
                self.assertEqual("old SHA\n", self.marker.read_text())

    def test_backup_failure_does_not_activate_or_change_release(self):
        result = self.execute(self.scripts / "build-prod.sh", "--backup-before-activate", TEST_BACKUP_EXIT="1")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("activate", self.events.read_text())
        self.assertEqual("old JAR", self.jar.read_text())
        self.assertEqual("old SHA\n", self.marker.read_text())

    def test_activation_preflight_failure_restores_disk_jar_and_marker(self):
        result = self.execute(self.scripts / "build-prod.sh", "--backup-before-activate", TEST_ACTIVATE_EXIT="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("old JAR", self.jar.read_text())
        self.assertEqual("old SHA\n", self.marker.read_text())

    def test_build_only_keeps_running_release_and_skips_backup_and_activation(self):
        result = self.execute(self.scripts / "build-prod.sh", "--build-only")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("backup", self.events.read_text())
        self.assertNotIn("activate", self.events.read_text())
        self.assertEqual("old JAR", self.jar.read_text())


class DatabaseBackupTest(ShellFixture):
    def setUp(self):
        super().setUp()
        shutil.copy(SCRIPTS / "backup-database.sh", self.scripts / "backup-database.sh")
        self.tool("docker", '''if [[ "$1" == inspect ]]; then
 if [[ "${2:-}" == -f ]]; then echo true; fi
 exit 0
fi
printf 'CREATE DATABASE test;\\nCREATE TABLE test(id INT);\\n'
if [[ "${TEST_DUMP_TRUNCATED:-false}" != true ]]; then printf -- '-- Dump completed on test\\n'; fi
exit "${TEST_DUMP_EXIT:-0}"
''')

    def test_completed_dump_is_valid_private_gzip(self):
        result = self.execute(self.scripts / "backup-database.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        backups = list((self.base / "backups").glob("*.sql.gz"))
        self.assertEqual(1, len(backups))
        self.assertEqual(0o600, backups[0].stat().st_mode & 0o777)
        self.assertIn("Dump completed", gzip.decompress(backups[0].read_bytes()).decode())

    def test_failed_or_truncated_dump_never_publishes_backup(self):
        for environment in ({"TEST_DUMP_EXIT": "1"}, {"TEST_DUMP_TRUNCATED": "true"}):
            with self.subTest(environment=environment):
                result = self.execute(self.scripts / "backup-database.sh", **environment)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], list((self.base / "backups").glob("*.sql.gz")))
                self.assertEqual([], list((self.base / "backups").glob("*.tmp")))


class BackendRollbackTest(ShellFixture):
    def setUp(self):
        super().setUp()
        source = (SCRIPTS / "run.sh").read_text()
        credential = self.base / "fake-credential.json"
        credential.write_text("{}")
        # Override just the fixture credential location; execute the real rollout functions.
        source = source.replace('CREDENTIAL_FILE="$HOME/.cloudflared/$TUNNEL_ID.json"', f'CREDENTIAL_FILE="{credential}"')
        (self.scripts / "run.sh").write_text(source)
        target = self.root / "backend/target"
        target.mkdir(parents=True)
        self.jar = target / "booking-system-0.0.1-SNAPSHOT.jar"
        self.jar.write_text("new JAR")
        Path(str(self.jar) + ".previous").write_text("old JAR")
        self.marker = target / "release-version.txt"
        self.marker.write_text("new SHA\n")
        Path(str(self.marker) + ".previous").write_text("old SHA\n")
        self.environment.update({"HR_PRODUCTION_ATTENDANCE_ENABLED": "false", "BACKEND_STARTUP_TIMEOUT": "1",
                                 "TEST_SERVICE_STATE": str(self.base / "service-state"), "TEST_START_COUNT": str(self.base / "start-count")})
        self.tool("docker", '''if [[ "$1" == exec && "$*" == *--execute* ]]; then echo 1; fi
''')
        self.tool("systemctl", '''if [[ "$*" == *is-active* && "$*" == *bookingbase-backend* ]]; then
 [[ -f "$TEST_SERVICE_STATE" && "$(cat "$TEST_SERVICE_STATE")" != stopped ]]
elif [[ "$*" == *stop* && "$*" == *bookingbase-backend* ]]; then echo stopped > "$TEST_SERVICE_STATE"; fi
''')
        self.tool("systemd-run", '''count=0; [[ ! -f "$TEST_START_COUNT" ]] || count="$(cat "$TEST_START_COUNT")"
count=$((count+1)); echo "$count" > "$TEST_START_COUNT"
[[ "${TEST_START_MODE:-start-fail}" != all-fail ]] || exit 1
if [[ "$count" == 1 ]]; then
 [[ "${TEST_START_MODE:-start-fail}" != start-fail ]] || exit 1
 echo new > "$TEST_SERVICE_STATE"
else
 echo old > "$TEST_SERVICE_STATE"
fi
''')
        self.tool("curl", '[[ "$(cat "$TEST_SERVICE_STATE")" == old ]]\n')
        for name in ("java", "cloudflared"):
            self.tool(name, "exit 0\n")

    def test_start_command_failure_rolls_back_and_restarts_old_jar(self):
        self.assertRollback("start-fail")

    def test_health_failure_rolls_back_and_restarts_old_jar(self):
        self.assertRollback("health-fail")

    def test_failed_old_start_is_reported_without_claiming_successful_rollback(self):
        result = self.execute(self.scripts / "run.sh", TEST_START_MODE="all-fail")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("rollback that bai", result.stderr)
        self.assertNotIn("Rollback hoan tat", result.stdout)
        self.assertEqual("old JAR", self.jar.read_text())

    def assertRollback(self, mode):
        result = self.execute(self.scripts / "run.sh", TEST_START_MODE=mode)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Rollback hoan tat", result.stdout)
        self.assertEqual("old JAR", self.jar.read_text())
        self.assertEqual("old SHA\n", self.marker.read_text())
        self.assertEqual("old\n", (self.base / "service-state").read_text())


if __name__ == "__main__":
    unittest.main()
