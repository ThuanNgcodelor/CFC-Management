"""Safety checks for the isolated QA runner; no Docker required."""
import importlib.util
from pathlib import Path
import tempfile
import subprocess
from types import SimpleNamespace
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("health_mysql_qa", Path(__file__).with_name("mysql_acceptance.py"))
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class MysqlAcceptanceSafetyTest(unittest.TestCase):
    def test_external_database_argument_is_rejected_before_docker(self):
        with patch("sys.argv", ["qa", "--url", "jdbc:mysql://192.168.1.1/production"]), patch.object(qa, "run") as run:
            with self.assertRaises(SystemExit) as failure:
                qa.main()
            self.assertEqual(2, failure.exception.code)
            run.assert_not_called()

    def test_restore_difference_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(qa, "run"), patch.object(qa, "mysql"), patch.object(qa, "digest", side_effect=[{"test": {"rows": 1}}, {"test": {"rows": 0}}]):
            with self.assertRaisesRegex(RuntimeError, "differ"):
                qa.restore_drill("isolated-qa", Path(folder))
            self.assertEqual(0o600, (Path(folder) / "synthetic-mysql.sql").stat().st_mode & 0o777)

    def test_unexpected_table_name_is_rejected_before_interpolation(self):
        with patch.object(qa, "mysql", return_value=b"unsafe`;DROP TABLE users\n") as mysql:
            with self.assertRaisesRegex(RuntimeError, "Unexpected"):
                qa.digest("isolated-qa", qa.SCHEMA)
            self.assertEqual(1, mysql.call_count)


class MysqlReadinessTest(unittest.TestCase):
    def test_waits_for_tcp_application_account_and_schema_instead_of_root_socket(self):
        errors = [subprocess.CalledProcessError(1, ["probe"], stderr=b"ERROR 2003 (HY000): unavailable"),
                  subprocess.CalledProcessError(1, ["probe"], stderr=b"ERROR 1045 (28000): account not ready"),
                  SimpleNamespace(stdout=b"1\n")]
        with patch.object(qa, "run", side_effect=errors) as run, patch.object(qa.time, "sleep"):
            qa.wait_for_mysql("qa-only")
        self.assertEqual(3, run.call_count)
        command = run.call_args.args[0]
        shell = command[-1]
        self.assertIn("--protocol=TCP --host=127.0.0.1", shell)
        self.assertIn("--user=health_qa", shell)
        self.assertIn("health_qa_acceptance", shell)
        self.assertIn('$MYSQL_PASSWORD', shell)
        self.assertNotIn('-uroot', shell)
        self.assertNotIn('MYSQL_ROOT_PASSWORD', shell)
        self.assertLessEqual(run.call_args.kwargs["timeout"], 10)

    def test_failed_readiness_never_creates_schema_or_launches_maven_and_cleans_up(self):
        with tempfile.TemporaryDirectory() as folder:
            private = Path(folder) / "private"
            private.mkdir()
            with patch("sys.argv", ["qa", "--maven", "/fake/mvn"]), \
                 patch.object(qa.shutil, "which", return_value="installed"), \
                 patch.object(qa.tempfile, "mkdtemp", return_value=str(private)), \
                 patch.object(qa, "run") as run, \
                 patch.object(qa, "wait_for_mysql", side_effect=RuntimeError("not ready")), \
                 patch.object(qa, "mysql") as mysql, \
                 patch.object(qa.subprocess, "run") as cleanup:
                with self.assertRaisesRegex(RuntimeError, "not ready"):
                    qa.main()
            self.assertEqual(1, run.call_count)  # docker run only
            mysql.assert_not_called()
            cleanup.assert_called_once()
            self.assertFalse((private / "mysql.env").exists())

    def test_timeout_is_bounded_and_reports_only_safe_error_codes(self):
        clock = [0.0]
        def sleep(seconds):
            clock[0] += seconds
        error = subprocess.CalledProcessError(1, ["probe"], stderr=b"ERROR 1045 (28000): private-password SQL-body")
        with patch.object(qa, "run", side_effect=error), \
             patch.object(qa.time, "monotonic", side_effect=lambda: clock[0]), \
             patch.object(qa.time, "sleep", side_effect=sleep):
            with self.assertRaises(RuntimeError) as failure:
                qa.wait_for_mysql("qa-only", timeout=2)
        self.assertEqual(2, clock[0])
        self.assertIn("1045", str(failure.exception))
        self.assertNotIn("private-password", str(failure.exception))
        self.assertNotIn("SQL-body", str(failure.exception))

    def test_command_timeout_is_retried_before_success(self):
        with patch.object(qa, "run", side_effect=[subprocess.TimeoutExpired("probe", 10), SimpleNamespace(stdout=b"1")]) as run, patch.object(qa.time, "sleep"):
            qa.wait_for_mysql("qa-only")
        self.assertEqual(2, run.call_count)

    def test_schema_failure_reports_mysql_code_without_raw_sql_or_credentials(self):
        error = subprocess.CalledProcessError(1, ["docker"], stderr=b"ERROR 1410 (42000): private-password SQL-body")
        with patch.object(qa, "run", side_effect=error):
            with self.assertRaises(RuntimeError) as failure:
                qa.mysql("qa-only", "secret SQL", operation="schema QA")
        self.assertIn("schema QA", str(failure.exception))
        self.assertIn("MySQL ERROR 1410, SQLSTATE 42000", str(failure.exception))
        self.assertNotIn("private-password", str(failure.exception))
        self.assertNotIn("secret SQL", str(failure.exception))
        self.assertNotIn("SQL-body", str(failure.exception))


if __name__ == "__main__":
    unittest.main()
