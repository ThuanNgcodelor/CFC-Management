"""Safety checks for the isolated QA runner; no Docker required."""
import importlib.util
from pathlib import Path
import tempfile
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


if __name__ == "__main__":
    unittest.main()
