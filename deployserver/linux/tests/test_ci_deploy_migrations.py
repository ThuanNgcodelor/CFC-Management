"""Git blob hashes and baseline diffs, without touching a production database."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_deploy_migrations as policy


class MigrationPolicyTest(unittest.TestCase):
    old_path = "backend/src/main/resources/db/migration/V32__old.sql"
    new_path = "backend/src/main/resources/db/migration/V33__new.sql"

    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.git("init", "--initial-branch=main")
        self.write(self.old_path, "CREATE TABLE old_table(id INT);")
        self.baseline = self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], text=True, stderr=subprocess.DEVNULL).strip()

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def commit(self):
        self.git("add", ".")
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.test", "commit", "-m", "fixture")
        return self.git("rev-parse", "HEAD")

    def declare(self, path=None, **overrides):
        path = path or self.new_path
        self.write(path, "CREATE TABLE new_table(id INT);")
        entry = {"sha256": hashlib.sha256((self.root / path).read_bytes()).hexdigest(),
                 "backwardCompatible": True, "reason": "Adds an independent table", **overrides}
        self.write(policy.MANIFEST, json.dumps({"version": 1, "migrations": {path: entry}}))

    def test_new_reviewed_sql_requires_backup_and_reads_candidate_not_working_tree(self):
        self.declare()
        candidate = self.commit()
        self.write(policy.MANIFEST, "local uncommitted text must not authorize/reject a release")
        self.assertTrue(policy.require_auto_migrations(self.root, self.baseline, candidate))

    def test_no_schema_changes_need_no_manifest_or_backup(self):
        self.write("app.txt", "new app")
        self.assertFalse(policy.require_auto_migrations(self.root, self.baseline, self.commit()))

    def test_missing_declaration_or_changed_hash_rejected(self):
        for overrides in ({"sha256": "0" * 64}, {"backwardCompatible": False}, {"reason": ""}):
            self.declare(**overrides)
            with self.subTest(overrides=overrides), self.assertRaises(RuntimeError):
                policy.require_auto_migrations(self.root, self.baseline, self.commit())
        (self.root / policy.MANIFEST).unlink()
        with self.assertRaises(RuntimeError):
            policy.require_auto_migrations(self.root, self.baseline, self.commit())

    def test_modified_old_migration_is_rejected_even_with_declaration(self):
        self.declare(self.old_path)
        with self.assertRaises(RuntimeError):
            policy.require_auto_migrations(self.root, self.baseline, self.commit())

    def test_deleted_migration_is_rejected(self):
        self.declare()
        (self.root / self.old_path).unlink()
        with self.assertRaises(RuntimeError):
            policy.require_auto_migrations(self.root, self.baseline, self.commit())

    def test_java_and_out_of_order_versions_stay_manual(self):
        for path in ("backend/src/main/java/db/migration/V34__new.java", "backend/src/main/resources/db/migration/V31__late.sql"):
            self.declare(path)
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                policy.require_auto_migrations(self.root, self.baseline, self.commit())

    def test_repository_declarations_match_reviewed_files(self):
        root = Path(__file__).resolve().parents[3]
        manifest = json.loads((root / policy.MANIFEST).read_text())
        for path, entry in manifest["migrations"].items():
            with self.subTest(path=path):
                self.assertIs(entry["backwardCompatible"], True)
                self.assertTrue(entry["reason"].strip())
                self.assertEqual(entry["sha256"], hashlib.sha256((root / path).read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
